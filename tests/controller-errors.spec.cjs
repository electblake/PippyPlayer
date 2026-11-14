const {test,expect}=require('playwright/test');
const fs=require('node:fs');
const path=require('node:path');

test.use({channel:'msedge',viewport:{width:390,height:844}});

const player=id=>({id,name:'Player '+id,url:'',size:30,zoom:1,volume:50,position:'BOTTOM_RIGHT',sorting:'DEFAULT',repeatOne:false,queueLength:0,queueIndex:0,visible:false,seekable:false,hasPrevious:false,hasNext:false,error:null,loading:false,playbackState:'Stopped',debugText:''});

test('source error survives polling, layout changes, tab switches and reload until the next source attempt',async({page})=>{
    const state={players:[player('1'),player('2')],recentUrls:[],recentUrlNames:{},debugEnabled:false};
    const failure='java.io.IOException: Server returned HTTP response code: 500 for URL: http://upnp.test/ctl\n    at app.pippyplayer.tv.DlnaPlaylist.load(DlnaPlaylist.kt:42)';
    let attempts=0,reads=0;
    await page.route('http://pippy.test/**',async route=>{
        const request=route.request(),url=new URL(request.url());
        if(url.pathname==='/session')return route.fulfill({contentType:'text/html',body:fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller.html'),'utf8')});
        if(url.pathname==='/session/state'){reads++;return route.fulfill({json:state});}
        if(url.pathname==='/session/players/1/source'){
            attempts++;
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
        if(url.pathname==='/session/state'){reads++;return route.fulfill({json:state});}
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
        return route.fulfill({json:state});
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
        return route.fulfill({json:state});
    });
    await page.goto('http://pippy.test/session');
    await page.getByRole('tab',{name:'Player 1',exact:true}).waitFor();
    await page.clock.runFor(1600);
    await expect.poll(()=>reads).toBe(2);
    await page.clock.runFor(6000);
    expect(reads).toBe(2);
    release();
});
