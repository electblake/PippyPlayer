const {test,expect}=require('playwright/test');
const fs=require('node:fs');
const path=require('node:path');

test.use({channel:'msedge',viewport:{width:390,height:844}});

let responseRevision=0;
const stateResponse=state=>({...state,revision:++responseRevision});
const player=id=>({sourceRevision:0,sourceRequestId:'',sourceLoading:false,id,name:'Player '+id,url:'',size:30,zoom:1,volume:50,position:'BOTTOM_RIGHT',sorting:'DEFAULT',repeatOne:false,queueLength:0,queueIndex:0,visible:false,seekable:false,hasPrevious:false,hasNext:false,error:null,loading:false,playbackState:'Stopped',debugText:''});

test('source error survives polling, layout changes, tab switches and reload until the next source attempt',async({page})=>{
    const state={players:[player('1'),player('2')],recentUrls:[],recentUrlNames:{},debugEnabled:false};
    const failure='java.io.IOException: Server returned HTTP response code: 500 for URL: http://upnp.test/ctl\n    at app.pippyplayer.tv.DlnaPlaylist.load(DlnaPlaylist.kt:42)';
    let attempts=0,reads=0;
    await page.route('http://pippy.test/**',async route=>{
        const request=route.request(),url=new URL(request.url());
        if(url.pathname==='/session')return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller.html'),'utf8')});
        if(url.pathname==='/session/state'){reads++;return route.fulfill({json:stateResponse(state)});}
        if(url.pathname==='/session/players/1/source'){
            attempts++;
            state.players[0].sourceRevision++;
            state.players[0].sourceRequestId=new URLSearchParams(request.postData()).get('requestId');
            if(attempts===1){state.players[0].error=failure;return route.fulfill({status:500,body:'Internal Server Error'});}
            state.players[0].error=null;state.players[0].queueLength=1;state.players[0].playbackState='Playing';
        }
        return route.fulfill({body:'OK'});
    });
    await page.goto('http://pippy.test/session');
    await page.locator('#player-1-url').fill('http://stash.test/performers/1');
    await page.locator('#player-panel-1 .primary').click();
    await expect(page.locator('#player-1-requestError')).toHaveText(failure);
    const initialReads=reads;
    await expect.poll(()=>reads,{timeout:10000}).toBeGreaterThanOrEqual(initialReads+3);
    await expect(page.locator('#player-1-requestError')).toHaveText(failure);
    await page.locator('#player-1-volume').fill('27');
    await page.locator('#player-1-volume').dispatchEvent('change');
    await page.getByRole('tab',{name:'Player 2',exact:true}).click();
    await expect(page.locator('#player-2-requestError')).toBeHidden();
    await page.getByRole('tab',{name:'Player 1',exact:true}).click();
    await expect(page.locator('#player-1-requestError')).toHaveText(failure);
    await page.reload();
    await expect(page.locator('#player-1-requestError')).toHaveText(failure);
    await page.locator('#player-1-url').fill('http://media.test/video');
    await page.locator('#player-panel-1 .primary').click();
    await expect(page.locator('#player-1-requestError')).toBeHidden();
    await expect(page.locator('#player-1-status')).toHaveText('Playing');
    expect(attempts).toBe(2);
});

test('HTTP response error stays readable even when state has no media error',async({page})=>{
    const state={players:[player('1')],recentUrls:[],recentUrlNames:{},debugEnabled:false};
    let reads=0;
    await page.route('http://pippy.test/**',async route=>{
        const url=new URL(route.request().url());
        if(url.pathname==='/session')return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller.html'),'utf8')});
        if(url.pathname==='/session/state'){reads++;return route.fulfill({json:stateResponse(state)});}
        if(url.pathname.endsWith('/source'))return route.fulfill({status:500,body:'Original source failure'});
        return route.fulfill({body:'OK'});
    });
    await page.goto('http://pippy.test/session');
    await page.locator('#player-1-url').fill('http://media.test/video');
    await page.locator('#player-panel-1 .primary').click();
    await expect(page.locator('#player-1-requestError')).toContainText('Original source failure');
    const initialReads=reads;
    await expect.poll(()=>reads,{timeout:10000}).toBeGreaterThanOrEqual(initialReads+3);
    await expect(page.locator('#player-1-requestError')).toContainText('500 Internal Server Error');
    await expect(page.locator('#player-panel-1 .primary')).toBeEnabled();
    await expect(page.locator('#player-1-status')).not.toHaveClass('loading');
});

test('playing state hides a stale spinner without hiding retained request errors',async({page})=>{
    const state={players:[player('1'),{...player('2'),playing:true,loading:true,loadingText:'Loading…',queueLength:1778,playbackState:'Playing',error:'Previous source request failed'}],recentUrls:[],recentUrlNames:{},debugEnabled:false};
    await page.route('http://pippy.test/**',async route=>{
        if(new URL(route.request().url()).pathname==='/session')return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller.html'),'utf8')});
        return route.fulfill({json:stateResponse(state)});
    });
    await page.goto('http://pippy.test/session');
    await page.getByRole('tab',{name:'Player 2',exact:true}).click();
    await expect(page.locator('#player-2-status')).toHaveText('Playing');
    await expect(page.locator('#player-2-status')).not.toHaveClass('loading');
    await expect(page.locator('#player-2-status')).toHaveAttribute('aria-busy','false');
    await expect(page.locator('#player-2-requestError')).toHaveText('Previous source request failed');
    await expect(page.locator('#player-2-nowPlaying')).toHaveText('1 / 1778');
});

test('slow state requests do not accumulate overlapping polling requests',async({page})=>{
    const state={players:[player('1')],recentUrls:[],recentUrlNames:{},debugEnabled:false};
    let reads=0,release;
    await page.clock.install();
    await page.route('http://pippy.test/**',async route=>{
        if(new URL(route.request().url()).pathname==='/session')return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller.html'),'utf8')});
        reads++;
        if(reads===2)await new Promise(resolve=>release=resolve);
        return route.fulfill({json:stateResponse(state)});
    });
    await page.goto('http://pippy.test/session');
    await page.getByRole('tab',{name:'Player 1',exact:true}).waitFor();
    await page.clock.runFor(1600);
    await expect.poll(()=>reads).toBe(2);
    await page.clock.runFor(6000);
    expect(reads).toBe(2);
    release();
});

test('controls sync across remotes while URL drafts survive and config writes change only one field',async({page})=>{
    const state={players:[player('1'),player('2')],recentUrls:[],recentUrlNames:{},debugEnabled:false};
    const configs=[];
    await page.route('http://pippy.test/**',async route=>{
        const req=route.request(),url=new URL(req.url());
        if(url.pathname==='/session')return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller.html'),'utf8')});
        if(url.pathname==='/session/state')return route.fulfill({json:stateResponse(state)});
        const fields=new URLSearchParams(req.postData());
        if(url.pathname.endsWith('/config')){configs.push([...fields.keys()]);state.players[0].volume=Number(fields.get('volume'));}
        if(url.pathname.endsWith('/source')){
            expect([...fields.keys()].sort()).toEqual(['requestId','url']);
            Object.assign(state.players[0],{sourceRevision:1,sourceRequestId:fields.get('requestId'),url:fields.get('url'),playing:true,queueLength:1,playbackState:'Playing'});
        }
        return route.fulfill({body:'OK'});
    });
    await page.goto('http://pippy.test/session');
    await page.locator('#player-1-url').fill('https://draft.test/video');
    Object.assign(state.players[0],{size:60,zoom:2,volume:80,position:'TOP_LEFT',url:'https://other-remote.test/video'});
    await expect(page.locator('#player-1-size')).toHaveValue('60');
    await expect(page.locator('#player-1-volume')).toHaveValue('80');
    await expect(page.locator('#player-1-url')).toHaveValue('https://draft.test/video');
    await page.locator('#player-1-volume').fill('17');
    await expect.poll(()=>configs.length).toBe(1);
    expect(configs).toEqual([['volume']]);
    expect(state.players[0].size).toBe(60);
    expect(state.players[1].volume).toBe(50);
    await page.locator('#player-panel-1 .primary').click();
    await expect(page.locator('#player-1-status')).toHaveText('Playing');
    expect(configs.length).toBe(1);
});

test('an older state response cannot resurrect a deleted player',async({page})=>{
    const state={players:[player('1'),player('2')],recentUrls:[],recentUrlNames:{},debugEnabled:false};
    let reads=0,release;
    await page.clock.install();
    await page.route('http://pippy.test/**',async route=>{
        const req=route.request(),url=new URL(req.url());
        if(url.pathname==='/session')return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller.html'),'utf8')});
        if(req.method()==='DELETE'){state.players=state.players.filter(p=>p.id!=='1');return route.fulfill({body:'OK'});}
        const snapshot=structuredClone(stateResponse(state));reads++;
        if(reads===2)await new Promise(resolve=>release=resolve);
        return route.fulfill({json:snapshot});
    });
    await page.goto('http://pippy.test/session');
    await page.getByRole('tab',{name:'Player 1',exact:true}).waitFor();
    await page.clock.runFor(1600);
    await expect.poll(()=>reads).toBe(2);
    await page.getByRole('button',{name:'Delete Player 1',exact:true}).click();
    await expect(page.locator('#player-panel-1')).toHaveCount(0);
    release();
    await page.clock.runFor(2000);
    await expect(page.locator('#player-panel-1')).toHaveCount(0);
    await expect(page.getByRole('tab',{name:'Player 2',exact:true})).toHaveAttribute('aria-selected','true');
});

test('reload sees pending source work and playback completion independently of a source HTTP response',async({page})=>{
    const state={players:[{...player('1'),sourceRevision:1,sourceRequestId:'pending',sourceLoading:true}],recentUrls:[],recentUrlNames:{},debugEnabled:false};
    await page.route('http://pippy.test/**',async route=>{
        if(new URL(route.request().url()).pathname==='/session')return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller.html'),'utf8')});
        return route.fulfill({json:stateResponse(state)});
    });
    await page.goto('http://pippy.test/session');
    await expect(page.locator('#player-1-status')).toHaveText('Loading source…');
    await expect(page.locator('#player-panel-1 .primary')).toBeDisabled();
    await expect(page.locator('#player-panel-1 [data-action=stop]')).toBeEnabled();
    Object.assign(state.players[0],{sourceLoading:false,playing:true,queueLength:1,playbackState:'Playing'});
    await expect(page.locator('#player-1-status')).toHaveText('Playing');
    await expect(page.locator('#player-panel-1 .primary')).toBeEnabled();
    await expect(page.locator('#player-1-status')).not.toHaveClass('loading');
});

test('stop aborts the local pending request without a stale error or a stuck loading state',async({page})=>{
    const state={players:[player('1')],recentUrls:[],recentUrlNames:{},debugEnabled:false};
    const errors=[];page.on('pageerror',error=>errors.push(error.message));
    let release;
    await page.route('http://pippy.test/**',async route=>{
        const req=route.request(),url=new URL(req.url());
        if(url.pathname==='/session')return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller.html'),'utf8')});
        if(url.pathname==='/session/state')return route.fulfill({json:stateResponse(state)});
        if(url.pathname.endsWith('/source')){
            Object.assign(state.players[0],{sourceLoading:true,sourceRevision:1,sourceRequestId:new URLSearchParams(req.postData()).get('requestId')});
            await new Promise(resolve=>release=resolve);
        }
        if(url.pathname.endsWith('/stop')){Object.assign(state.players[0],{sourceLoading:false,sourceRevision:2,sourceRequestId:''});release();}
        return route.fulfill({body:'OK'});
    });
    await page.goto('http://pippy.test/session');
    await page.locator('#player-1-url').fill('https://slow.test/video');
    await page.locator('#player-panel-1 .primary').click();
    await expect(page.locator('#player-panel-1 [data-action=stop]')).toBeEnabled();
    await page.locator('#player-panel-1 [data-action=stop]').click();
    await expect(page.locator('#player-panel-1 .primary')).toBeEnabled();
    await expect(page.locator('#player-1-requestError')).toBeHidden();
    await expect(page.locator('#player-1-status')).not.toHaveClass('loading');
    expect(errors).toEqual([]);
});

test('selecting a different recent URL supersedes a pending source without stale errors',async({page})=>{
    const state={players:[player('1')],recentUrls:['https://new.test/video'],recentUrlNames:{},debugEnabled:false};
    const errors=[];page.on('pageerror',error=>errors.push(error.message));
    const sources=[];let releaseOld;
    await page.route('http://pippy.test/**',async route=>{
        const req=route.request(),url=new URL(req.url());
        if(url.pathname==='/session')return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller.html'),'utf8')});
        if(url.pathname==='/session/state')return route.fulfill({json:stateResponse(state)});
        if(url.pathname.endsWith('/source')){
            const fields=new URLSearchParams(req.postData());sources.push(fields.get('url'));
            Object.assign(state.players[0],{sourceRevision:sources.length,sourceRequestId:fields.get('requestId'),sourceLoading:true});
            if(sources.length===1){await new Promise(resolve=>releaseOld=resolve);return route.fulfill({status:500,body:'Old cancelled request'});}
            Object.assign(state.players[0],{sourceLoading:false,url:fields.get('url'),playing:true,queueLength:1,playbackState:'Playing'});
            releaseOld();
        }
        return route.fulfill({body:'OK'});
    });
    await page.goto('http://pippy.test/session');
    await page.locator('#player-1-url').fill('https://old.test/video');
    await page.locator('#player-panel-1 .primary').click();
    await expect.poll(()=>sources.length).toBe(1);
    await page.locator('#player-1-recentSources summary').click();
    await page.locator('#player-1-recentUrls .recent-select').click();
    await expect(page.locator('#player-1-status')).toHaveText('Playing');
    await expect(page.locator('#player-1-requestError')).toBeHidden();
    await expect(page.locator('#player-1-url')).toHaveValue('https://new.test/video');
    expect(sources).toEqual(['https://old.test/video','https://new.test/video']);
    expect(errors).toEqual([]);
});

test('authoritative completed source state clears loading before its HTTP reply arrives',async({page})=>{
    const state={players:[player('1')],recentUrls:[],recentUrlNames:{},debugEnabled:false};
    let release;
    await page.route('http://pippy.test/**',async route=>{
        const req=route.request(),url=new URL(req.url());
        if(url.pathname==='/session')return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller.html'),'utf8')});
        if(url.pathname==='/session/state')return route.fulfill({json:stateResponse(state)});
        if(url.pathname.endsWith('/source')){
            const fields=new URLSearchParams(req.postData());
            Object.assign(state.players[0],{sourceRevision:1,sourceRequestId:fields.get('requestId'),sourceLoading:false,playing:true,queueLength:1,playbackState:'Playing'});
            await new Promise(resolve=>release=resolve);
        }
        return route.fulfill({body:'OK'});
    });
    await page.goto('http://pippy.test/session');
    await page.locator('#player-1-url').fill('https://media.test/video');
    await page.locator('#player-panel-1 .primary').click();
    await expect(page.locator('#player-1-status')).toHaveText('Playing');
    await expect(page.locator('#player-panel-1 .primary')).toBeEnabled();
    await expect(page.locator('#player-1-status')).not.toHaveClass('loading');
    release();
});
