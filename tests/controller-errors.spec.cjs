const {test,expect}=require('playwright/test');
const fs=require('node:fs');
const path=require('node:path');

test.use({channel:'msedge',viewport:{width:390,height:844}});

let responseRevision=0;
const stateResponse=state=>({...state,revision:++responseRevision});
const player=id=>({sourceRevision:0,sourceRequestId:'',sourceLoading:false,id,name:'Player '+id,url:'',size:30,zoom:1,volume:50,position:'BOTTOM_RIGHT',offsetX:0,offsetY:0,cropTop:0,cropRight:0,cropBottom:0,cropLeft:0,sorting:'DEFAULT',repeatOne:false,queueLength:0,queueIndex:0,visible:false,seekable:false,hasPrevious:false,hasNext:false,error:null,loading:false,playbackState:'Stopped',debugText:''});

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
    await expect(page.getByRole('button',{name:'Rename Player 1',exact:true})).toHaveCount(0);
    release();
    await page.clock.runFor(2000);
    await expect(page.locator('#player-panel-1')).toHaveCount(0);
    await expect(page.getByRole('tab',{name:'Player 2',exact:true})).toHaveAttribute('aria-selected','true');
});

test('position collapses per player and sends only the edited screen offset',async({page})=>{
    const state={players:[player('1'),player('2')],recentUrls:[],recentUrlNames:{},debugEnabled:false};
    const writes=[];
    await page.route('http://pippy.test/**',async route=>{
        const req=route.request(),url=new URL(req.url());
        if(url.pathname==='/session')return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller.html'),'utf8')});
        if(url.pathname==='/session/state')return route.fulfill({json:stateResponse(state)});
        expect(url.pathname).toBe('/session/players/1/config');
        const fields=[...new URLSearchParams(req.postData())];
        writes.push(fields);
        for(const [field,value] of fields)state.players[0][field]=Number(value);
        return route.fulfill({body:'OK'});
    });
    await page.goto('http://pippy.test/session');
    const first=page.locator('#player-panel-1');
    const position=first.getByText('Position',{exact:true});
    const x=first.getByLabel('Horizontal screen offset');
    const y=first.getByLabel('Vertical screen offset');
    await expect(first.getByRole('button',{name:'center',exact:false}).first()).toBeVisible();
    await position.click();
    await expect(x).toBeHidden();
    await expect(first.getByLabel('Size')).toBeVisible();
    await page.getByRole('tab',{name:'Player 2',exact:true}).click();
    await expect(page.locator('#player-panel-2').getByLabel('Horizontal screen offset')).toHaveValue('0');
    await page.getByRole('tab',{name:'Player 1',exact:true}).click();
    await expect(x).toBeHidden();
    await position.click();
    await x.fill('-24');
    await y.fill('13');
    await expect.poll(()=>writes).toEqual(expect.arrayContaining([[['offsetX','-24']],[['offsetY','13']]]));
    expect(writes).toHaveLength(2);
    await expect(first.locator('#player-1-offsetXValue')).toHaveText('-24%');
    await expect(first.locator('#player-1-offsetYValue')).toHaveText('13%');
    await page.reload();
    await expect(x).toHaveValue('-24');
    await expect(y).toHaveValue('13');
    await expect(first.getByRole('button',{name:'bottom right',exact:true})).toHaveAttribute('aria-pressed','true');
});

test('renaming a tab updates its labels across remotes and preserves the player and its URL draft',async({page,context})=>{
    const state={players:[player('1'),player('2')],recentUrls:[],recentUrlNames:{},debugEnabled:false};
    const names=[];
    await context.route('http://pippy.test/**',async route=>{
        const req=route.request(),url=new URL(req.url());
        if(url.pathname==='/session')return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller.html'),'utf8')});
        if(url.pathname==='/session/state')return route.fulfill({json:stateResponse(state)});
        expect(req.method()).toBe('POST');
        expect(url.pathname).toBe('/session/players/2/name');
        const name=new URLSearchParams(req.postData()).get('name');
        names.push(name);
        state.players[1].name=name;
        return route.fulfill({body:'OK'});
    });
    const otherRemote=await context.newPage();
    await otherRemote.goto('http://pippy.test/session');
    await page.goto('http://pippy.test/session');
    await page.getByRole('tab',{name:'Player 1',exact:true}).press('ArrowRight');
    await page.locator('#player-2-url').fill('https://draft.test/video');
    await page.getByRole('tab',{name:'Player 2',exact:true}).press('Tab');
    const pencil=page.getByRole('button',{name:'Rename Player 2',exact:true});
    await expect(pencil).toBeFocused();
    page.once('dialog',dialog=>dialog.dismiss());
    await page.keyboard.press('Enter');
    expect(names).toEqual([]);

    const name='Kitchen & <TV>';
    page.once('dialog',dialog=>dialog.accept(name));
    await page.keyboard.press('Enter');
    await expect(page.getByRole('tab',{name,exact:true})).toHaveAttribute('aria-selected','true');
    await expect(page.getByRole('heading',{name,exact:true})).toBeVisible();
    await expect(page.getByRole('button',{name:'Delete '+name,exact:true})).toBeVisible();
    await expect(page.locator('#player-2-url')).toHaveValue('https://draft.test/video');
    await expect(otherRemote.getByRole('tab',{name,exact:true})).toBeVisible();
    await expect(otherRemote.getByRole('tab',{name:'Player 1',exact:true})).toHaveAttribute('aria-selected','true');
    await page.reload();
    await expect(page.getByRole('button',{name:'Rename '+name,exact:true})).toBeVisible();
    expect(names).toEqual([name]);
});

test('cropping edits a single edge on the active player and restores all four edges on reload',async({page})=>{
    const state={players:[player('1'),{...player('2'),offsetX:12,cropTop:-7,cropRight:-12,cropBottom:-9}],recentUrls:[],recentUrlNames:{},debugEnabled:false};
    const writes=[];
    await page.route('http://pippy.test/**',async route=>{
        const req=route.request(),url=new URL(req.url());
        if(url.pathname==='/session')return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller.html'),'utf8')});
        if(url.pathname==='/session/state')return route.fulfill({json:stateResponse(state)});
        expect(url.pathname).toBe('/session/players/2/config');
        const fields=[...new URLSearchParams(req.postData())];
        writes.push(fields);
        for(const [field,value] of fields)state.players[1][field]=Number(value);
        return route.fulfill({body:'OK'});
    });
    await page.goto('http://pippy.test/session');
    await page.getByRole('tab',{name:'Player 2',exact:true}).click();
    const second=page.locator('#player-panel-2');
    const left=second.getByLabel('Left crop');
    await expect(left).toBeHidden();
    await second.getByText('Cropping',{exact:true}).click();
    await left.fill('-21');
    await expect.poll(()=>writes).toEqual([[['cropLeft','-21']]]);
    await expect(second.locator('#player-2-cropLeftValue')).toHaveText('-21%');
    await expect(second.getByLabel('Horizontal screen offset')).toHaveValue('12');
    await page.reload();
    await page.locator('#player-panel-1').getByText('Cropping',{exact:true}).click();
    await expect(page.locator('#player-panel-1').getByLabel('Left crop')).toHaveValue('0');
    await page.getByRole('tab',{name:'Player 2',exact:true}).click();
    await second.getByText('Cropping',{exact:true}).click();
    await expect(second.getByLabel('Top crop')).toHaveValue('-7');
    await expect(second.getByLabel('Right crop')).toHaveValue('-12');
    await expect(second.getByLabel('Bottom crop')).toHaveValue('-9');
    await expect(left).toHaveValue('-21');
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

test('recent URLs delete after the pencil, retain focus and stay deleted across players and reload',async({page})=>{
    const first='https://media.test/first',second='https://media.test/second';
    const state={players:[player('1'),player('2')],recentUrls:[first,second],recentUrlNames:{[first]:'First',[second]:'Second'},debugEnabled:false};
    let deleted;
    await page.route('http://pippy.test/**',async route=>{
        const request=route.request(),url=new URL(request.url());
        if(url.pathname==='/session')return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller.html'),'utf8')});
        if(url.pathname==='/session/state')return route.fulfill({json:stateResponse(state)});
        if(url.pathname==='/session/recent-url-delete'){
            expect(request.method()).toBe('POST');
            deleted=new URLSearchParams(request.postData()).get('url');
            state.recentUrls=state.recentUrls.filter(url=>url!==deleted);
            delete state.recentUrlNames[deleted];
            return route.fulfill({body:'OK'});
        }
        throw new Error('Unexpected request: '+request.url());
    });
    await page.goto('http://pippy.test/session');
    await page.locator('#player-1-recentSources summary').click();
    const list=page.locator('#player-1-recentUrls');
    await expect(list.locator('li').first().locator('button')).toHaveCount(3);
    await expect(list.locator('li').first().locator('button').nth(1)).toHaveAccessibleName('Edit name for First');
    await expect(list.locator('li').first().locator('button').nth(2)).toHaveAccessibleName('Delete First from recent URLs');
    await list.getByRole('button',{name:'Delete First from recent URLs',exact:true}).click();
    expect(deleted).toBe(first);
    await expect(list.locator('li')).toHaveCount(1);
    await expect(list.getByRole('button',{name:'Delete Second from recent URLs',exact:true})).toBeFocused();
    await expect(page.locator('#player-1-recentCount')).toHaveText('(1)');
    await page.getByRole('tab',{name:'Player 2',exact:true}).click();
    await page.locator('#player-2-recentSources summary').click();
    await expect(page.locator('#player-2-recentUrls li')).toHaveCount(1);
    await page.reload();
    await page.locator('#player-1-recentSources summary').click();
    await expect(list.locator('li')).toHaveCount(1);
    for(const colorScheme of ['light','dark']){
        await page.emulateMedia({colorScheme});
        await expect(list.locator('.recent-select')).toHaveCSS('font-size','13px');
        await expect(list.locator('.recent-select')).toHaveCSS('border-radius','0px');
        expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
        await page.screenshot({path:'artifacts/recent-urls-'+colorScheme+'.png',fullPage:true});
    }
    await list.getByRole('button',{name:'Delete Second from recent URLs',exact:true}).click();
    await expect(page.locator('#player-1-historySearch')).toBeFocused();
    await expect(page.locator('#player-1-historyEmpty')).toBeVisible();
    await expect(page.locator('#player-1-recentCount')).toHaveText('(0)');
});

test('positions are reserved across players during writes and freed after moves and deletion',async({page})=>{
    const state={players:[player('1'),{...player('2'),position:'TOP_LEFT'}],recentUrls:[],recentUrlNames:{},debugEnabled:false};
    let release;
    await page.route('http://pippy.test/**',async route=>{
        const req=route.request(),url=new URL(req.url());
        if(url.pathname==='/session')return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller.html'),'utf8')});
        if(url.pathname.endsWith('/config')){
            await new Promise(resolve=>release=resolve);
            state.players[0].position=new URLSearchParams(req.postData()).get('position');
            return route.fulfill({body:'OK'});
        }
        if(req.method()==='DELETE'){state.players.splice(0,1);return route.fulfill({body:'OK'});}
        return route.fulfill({json:stateResponse(state)});
    });
    await page.goto('http://pippy.test/session');
    const first=page.locator('#player-panel-1'),second=page.locator('#player-panel-2');
    await expect(first.locator('[data-position="TOP_LEFT"]')).toBeDisabled();
    await expect(second.locator('[data-position="BOTTOM_RIGHT"]')).toBeDisabled();
    await first.locator('[data-position="MIDDLE_CENTER"]').click();
    await expect.poll(()=>typeof release).toBe('function');
    await expect(first.locator('[data-position="TOP_RIGHT"]')).toBeDisabled();
    await page.getByRole('tab',{name:'Player 2',exact:true}).click();
    await expect(second.locator('[data-position="MIDDLE_CENTER"]')).toBeDisabled();
    await expect(second.locator('[data-position="BOTTOM_RIGHT"]')).toBeDisabled();
    release();
    await expect(second.locator('[data-position="BOTTOM_RIGHT"]')).toBeEnabled();
    await expect(second.locator('[data-position="MIDDLE_CENTER"]')).toBeDisabled();
    await page.getByRole('tab',{name:'Player 1',exact:true}).click();
    await first.getByRole('button',{name:'Delete Player 1',exact:true}).click();
    await expect(second.locator('[data-position="MIDDLE_CENTER"]')).toBeEnabled();
});

test('all nine occupied positions disable adding players',async({page})=>{
    const positions=['TOP_LEFT','TOP_CENTER','TOP_RIGHT','MIDDLE_LEFT','MIDDLE_CENTER','MIDDLE_RIGHT','BOTTOM_LEFT','BOTTOM_CENTER','BOTTOM_RIGHT'];
    const state={players:positions.map((position,i)=>({...player(String(i+1)),position})),recentUrls:[],recentUrlNames:{},debugEnabled:false};
    await page.route('http://pippy.test/**',route=>new URL(route.request().url()).pathname==='/session'
        ?route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller.html'),'utf8')})
        :route.fulfill({json:stateResponse(state)}));
    await page.goto('http://pippy.test/session');
    await expect(page.getByRole('button',{name:'Add player',exact:true})).toBeDisabled();
    await expect(page.locator('#player-panel-1 [data-position]:disabled')).toHaveCount(8);
    await expect(page.locator('#player-panel-1 [data-position="TOP_LEFT"]')).toBeEnabled();
});

test('diagnostic logs tail only while shown, preserve scroll, and copy on the HTTP remote',async({page})=>{
    const state={players:[player('1')],recentUrls:[],recentUrlNames:{},debugEnabled:false};
    let reads=0;
    let logs=Array.from({length:200},(_,i)=>`event ${i} <literal>`).join('\n');
    await page.route('http://pippy.test/**',route=>{
        const pathname=new URL(route.request().url()).pathname;
        if(pathname==='/session')return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller.html'),'utf8')});
        if(pathname==='/session/diagnostics'){reads++;return route.fulfill({contentType:'text/plain',body:logs});}
        return route.fulfill({json:stateResponse(state)});
    });
    await page.goto('http://pippy.test/session');
    await expect(page.locator('#player-panel-1')).toBeVisible();
    expect(reads).toBe(0);
    await expect(page.getByRole('button',{name:'Copy logs'})).toBeDisabled();
    await page.getByRole('button',{name:'Show logs'}).click();
    const viewer=page.getByRole('textbox',{name:'Diagnostic logs',exact:true});
    await expect(viewer).toHaveValue(logs);
    expect(await viewer.evaluate(e=>e.scrollTop+e.clientHeight>=e.scrollHeight-2)).toBe(true);
    await viewer.evaluate(e=>{e.scrollTop=0;});
    logs+='\nnew live event';
    await expect(viewer).toHaveValue(logs);
    expect(await viewer.evaluate(e=>e.scrollTop)).toBe(0);
    await page.getByRole('button',{name:'Copy logs'}).click();
    await page.evaluate(()=>{const paste=document.createElement('textarea');paste.id='pasteCheck';document.body.append(paste);paste.focus();});
    await page.keyboard.press('Control+V');
    await expect(page.locator('#pasteCheck')).toHaveValue(logs);
    await page.getByRole('button',{name:'Hide logs'}).click();
    const stoppedAt=reads;
    await page.waitForTimeout(3200);
    expect(reads).toBe(stoppedAt);
    await expect(viewer).toBeHidden();
    await expect(page.getByRole('button',{name:'Copy logs'})).toBeDisabled();
});

test('slow diagnostic requests never overlap and hiding ignores their late response',async({page})=>{
    const state={players:[],recentUrls:[],recentUrlNames:{},debugEnabled:false};
    let reads=0,release;
    await page.route('http://pippy.test/**',async route=>{
        const pathname=new URL(route.request().url()).pathname;
        if(pathname==='/session')return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller.html'),'utf8')});
        if(pathname==='/session/diagnostics'){
            reads++;
            await new Promise(resolve=>{release=resolve;});
            return route.fulfill({contentType:'text/plain',body:'late event'});
        }
        return route.fulfill({json:stateResponse(state)});
    });
    await page.goto('http://pippy.test/session');
    await page.getByRole('button',{name:'Show logs'}).click();
    await expect.poll(()=>reads).toBe(1);
    await page.waitForTimeout(3200);
    expect(reads).toBe(1);
    await page.getByRole('button',{name:'Hide logs'}).click();
    release();
    await expect(page.locator('#diagnosticPanel')).toBeHidden();
    await expect(page.locator('#copyLogs')).toBeDisabled();
    await expect(page.locator('#diagnosticLogs')).toHaveValue('');
});
