//Constants
const GRAVITY = 0;
const PLAYER_SPAWN = {x: 0, y: 240};
const PLAYER1_SPAWN = { x: 0, y: -240 };
const AI_MAX_OFFSET = 18;
const BALL_SPAWN = {x: 0, y: PLAYER_SPAWN.y - 20};
const LIFE_SPAWN = { x: 0, y: 275 };
const LIFE1_SPAWN = { x: 0, y: -275 };
const PLAYER_SPEED = 6;
const BALL_SPEED = 6;
const LIFE = 16;
const LIFE_TILE = 40;
const LIFE_GAP = 10;
const LIFE_STRIDE = LIFE_TILE + LIFE_GAP;
const UI_OFF = { x: 0, y: 2500 };

function parkUI(sprite) {
    sprite.visible = false;
    sprite.physicsEnabled = true;
    sprite.pos = { x: UI_OFF.x, y: UI_OFF.y };
}

function placeUI(sprite, x, y) {
    sprite.physicsEnabled = true;
    sprite.visible = true;
    sprite.pos = { x, y };
}

function pointerHits(sprite) {
    if (!sprite.visible) return false;
    return abs(pointer.x - sprite.x) <= sprite.width / 2 && abs(pointer.y - sprite.y) <= sprite.height / 2;
}

//functions
function titleImage() {
    let timg = new Sprite(titleImg);
    timg.x = 0;
    timg.y = -100;
    timg.scale = 2;
    timg.physics = STATIC;
    return timg;
}

function newGameButton() {
    let ngb = new Sprite(newGameDefault);
    ngb.x = 0;
    ngb.y = 30;
    ngb.scale = 1;
    ngb.physics = STATIC;
    //ngb.debug = true;

    return ngb;
}

function multiplayerButton() {
    let mpb = new Sprite(multiplayerDefault);
    mpb.x = 0;
    mpb.y = 100;
    mpb.scale = 1;
    mpb.physics = STATIC;

    return mpb;
}

function settingsButton() {
    let sb = new Sprite(settingsDefault);
    sb.x = 0;
    sb.y = 170;
    sb.scale = 1;
    sb.physics = STATIC;

    return sb;
}

function continueButton() {
    let cb = new Sprite(continueDefault);
    cb.scale = 1;
    cb.physics = STATIC;
    parkUI(cb);

    return cb
}

function exitButton() {
    let eb = new Sprite(exitDefault);
    eb.scale = 1;
    eb.physics = STATIC;
    parkUI(eb);

    return eb;
}

function backToMainMenuButton() {
    let mmb = new Sprite(mainMenuDefault);
    mmb.scale = 1;
    mmb.physics = STATIC;
    parkUI(mmb);

    return mmb;
}

let blockHover = 0;
let menuIndex = 0;
let menuStickReady = true;
let lastPointerX = 0;
let lastPointerY = 0;

function playHover(sprite, over) {
    if (blockHover > 0) {
        sprite.wasHovered = over;
        return;
    }

    if (over && !sprite.wasHovered) {
        hoverSnd.stop();
        hoverSnd.play();
    }
    sprite.wasHovered = over;
}

function playSelect() {
    hoverSnd.stop();
    selectedSnd.stop();
    selectedSnd.play();
    blockHover = 2;
}

function endHoverPass() {
    if (blockHover > 0) {
        blockHover -= 1;
    }
}

function moveMenuCursor(rows) {
    let pointerMoved = pointer.x !== lastPointerX || pointer.y !== lastPointerY;
    lastPointerX = pointer.x;
    lastPointerY = pointer.y;

    if (pointerMoved || mouse.presses()) {
        for (let i = 0; i < rows.length; i++) {
            if (pointer.overlapping(rows[i])) {
                menuIndex = i;
            }
        }
    }

    if (!mouse.presses()) {
        if (contro.presses('down')) menuIndex += 1;
        if (contro.presses('up')) menuIndex -= 1;

        if (abs(contro.leftStick.y) < 0.6) {
            menuStickReady = true;

        } else if (menuStickReady) {
            menuStickReady = false;
            if (contro.leftStick.y > 0) menuIndex += 1;
            else menuIndex -= 1;
        }
    }

    let last = rows.length - 1;
    if (menuIndex > last) menuIndex = 0;
    if (menuIndex < 0) menuIndex = last;
}

function showMenu() {
    background('black');
    imageMode(CENTER);
    image(menuBG, 0, 0, 800, 600);
    image(titleImg, 0, -150);

    placeUI(newGameBtn, 0, 30);
    placeUI(multiplayerBtn, 0, 100);
    placeUI(settingsBtn, 0, 170);
    

    let canClick = frameCount > 30;
    moveMenuCursor([newGameBtn, multiplayerBtn, settingsBtn]);

    let overNewGame = canClick && menuIndex === 0;
    let overMultiplayer = canClick && menuIndex === 1;
    let overSettings = canClick && menuIndex === 2;

    playHover(newGameBtn, overNewGame);
    playHover(multiplayerBtn, overMultiplayer);
    playHover(settingsBtn, overSettings);

    if (overNewGame) {
        newGameBtn.img = newGameSelected;
        if ((mouse.presses() && pointer.overlapping(newGameBtn)) || contro.presses('a')) {
            playSelect();
            gameState = 'newGame';
            startNewGame();
        } 

    } else {
        newGameBtn.img = newGameDefault;
    }

    if (overMultiplayer) {
        multiplayerBtn.img = multiplayerSelected;
        if ((mouse.presses() && pointer.overlapping(multiplayerBtn)) || contro.presses('a')) {
            playSelect();
            //gameState = 'multiplayer';
        }

    } else {
        multiplayerBtn.img = multiplayerDefault;
    }

    if (overSettings) {
        settingsBtn.img = settingsSelected;
        if ((mouse.presses() && pointer.overlapping(settingsBtn)) || contro.presses('a')) {
            playSelect();
            settingsFrom = 'menu';
            gameState = 'settings';
        }

    } else {
            settingsBtn.img = settingsDefault;
    }

    endHoverPass();
}

function pauseGame() {
    if (!kb.presses('escape') && !contro.presses('start')) {
        return;
    }

    if (gameState === 'newGame') {
        menuIndex = 0;
        continueBtn.wasHovered = true;
        gameState = 'paused';
        world.timeScale = 0;

    } else if (gameState === 'paused') {
        resumeGame();
    }
}

function pauseMenu() {
    player0.visible = false;
    player0.fill = color(0, 0, 0, 0);
    player0.stroke = color(0, 0, 0, 0);
    player0.strokeWeight = 0;

    player1.visible = false;
    player1.fill = color(0, 0, 0, 0);
    player1.stroke = color(0, 0, 0, 0);
    player1.strokeWeight = 0;

    ball.visible = false;
    ball.fill = color(0, 0, 0, 0);
    ball.stroke = color(0, 0, 0, 0);
    ball.strokeWeight = 0;

    for (let box of lives) {
        box.visible = false;
        box.fill = color(0, 0, 0, 0);
        box.stroke = color(0, 0, 0, 0);
        box.strokeWeight = 0;
    }

    for (let box of lives1) {
        box.visible = false;
        box.fill = color(0, 0, 0, 0);
        box.stroke = color(0, 0, 0, 0);
        box.strokeWeight = 0;
    }

    parkUI(newGameBtn);
    parkUI(multiplayerBtn);

    placeUI(continueBtn, 0, -80);
    placeUI(settingsBtn, 0, 0);
    placeUI(exitBtn, 0, 80);

    let canClick = frameCount > 30;
    moveMenuCursor([continueBtn, settingsBtn, exitBtn]);

    let overContinue = canClick && menuIndex === 0;
    let overSettings = canClick && menuIndex === 1;
    let overExit = canClick && menuIndex === 2;

    playHover(continueBtn, overContinue);
    playHover(settingsBtn, overSettings);
    playHover(exitBtn, overExit);

    if (overContinue) {
        continueBtn.img = continueSelected;
        if ((mouse.presses() && pointer.overlapping(continueBtn)) || contro.presses('a')) {
            playSelect();
            resumeGame();
        }
    } else {
        continueBtn.img = continueDefault;
    }

    if (overSettings) {
        settingsBtn.img = settingsSelected;
        if ((mouse.presses() && pointer.overlapping(settingsBtn)) || contro.presses('a')) {
            playSelect();
            settingsFrom = 'paused';
            gameState = 'settings';
        }
    } else {
        settingsBtn.img = settingsDefault;
    }

    if (overExit) {
        exitBtn.img = exitSelected;
        if ((mouse.presses() && pointer.overlapping(exitBtn)) || contro.presses('a')) {
            playSelect();
            exitGame();
        }
    } else {
        exitBtn.img = exitDefault;
    }

    endHoverPass();

}

function gameOverMenu() {
    background('black');
    imageMode(CENTER);
    image(inGameBG, 0, 0, 800, 600);

    player0.visible = false;
    player0.physicsEnabled = false;

    ball.visible = false;
    ball.physicsEnabled = false;

    for (let box of lives) {
        box.visible = false;
        box.fill = color(0, 0, 0, 0);
        box.stroke = color(0, 0, 0, 0);
        box.strokeWeight = 0;
    }

    placeUI(newGameBtn, 0, -40);
    placeUI(mainMenuBtn, 0, 30);

    let canClick = frameCount > 30;
    moveMenuCursor([newGameBtn, mainMenuBtn]);

    let overNewGame = canClick && menuIndex === 0;
    let overMainMenu = canClick && menuIndex === 1;

    if (overNewGame) {
        newGameBtn.img = newGameSelected;
        if ((mouse.presses() && pointer.overlapping(newGameBtn)) || contro.presses('a')) {
            gameState = 'newGame';
            startNewGame();

        }

    } else {
        newGameBtn.img = newGameDefault;
    }

    if (overMainMenu) {
        mainMenuBtn.img = mainMenuSelected;
        if ((mouse.presses() && pointer.overlapping(mainMenuBtn)) || contro.presses('a')) {
            gameState = 'menu';
            parkUI(mainMenuBtn);
        }
    } else {
        mainMenuBtn.img = mainMenuDefault;
    }
}

function startNewGame() {
    blockHover = 0;
    world.timeScale = 1;
    parkUI(newGameBtn);
    parkUI(multiplayerBtn);
    parkUI(settingsBtn);
    parkUI(mainMenuBtn);

    player0.fill = 'red';
    player0.strokeWeight = 2;
    player1.fill = 'cyan';
    player1.strokeWeight = 2;
    ball.fill = 'white';
    ball.stroke = 'red';
    ball.strokeWeight = 2;
    
    applySpawn(player0, PLAYER_SPAWN);
    applySpawn(player1, PLAYER1_SPAWN);
    applySpawn(ball, BALL_SPAWN);

    resetLives();
    ballServed = false;
    lifeCount = 0
    ballSpeed = BALL_SPEED;
}

function resetRally() {
    let ballSide = lastHitter === player1 ? 20 : -20;
    ball.fill = 'white';
    applySpawn(player0, PLAYER_SPAWN);
    applySpawn(player1, PLAYER1_SPAWN);
    applySpawn(ball, {x: lastHitter.x, y: lastHitter.y + ballSide});
    ballServed = false;
    ballSpeed = BALL_SPEED;
}

function multiplayer() {
    gameState = 'multiplayer';
}

function settings() {
    background('black');
    imageMode(CENTER);
    image(menuBG, 0, 0, 800, 600);

    settingsWidget.visible = true;
    settingsWidget.physicsEnabled = true;


    parkUI(newGameBtn);
    parkUI(multiplayerBtn);
    parkUI(settingsBtn);
    parkUI(exitBtn);
    parkUI(continueBtn);

    let canClick = frameCount > 30;
    
    if (!kb.presses('escape') && !contro.presses('start')) {
        return;
    }

    gameState = settingsFrom;
    settingsWidget.visible = false;
    settingsWidget.physicsEnabled = false;
    
}

function makeWidget() {
    let w = new Sprite();
    w.x = 0;
    w.y = 0;
    w.width = 400;
    w.height = 300;
    w.fill = color(0, 0, 0, 0);
    w.stroke = color(1, 1, 1, 0.25);
    w.strokeWeight = 0;
    w.physics = STATIC;
    w.visible = false;
    w.physicsEnabled = false

    w.draw = function() {
        pushStyles();

        let sw = frostedBG.width / 2;
        let sh = frostedBG.height / 2;
        let sx = frostedBG.width / 4;
        let sy = frostedBG.height / 4;
        image(frostedBG, 0, 0, this.width, this.height, sx, sy, sw, sh);
        noFill();
        stroke(0.85, 0.7, 1);
        strokeWeight(2);
        rectMode(CENTER);
        rect(0, 0, this.width, this.height);

        popStyles();
    }
    return w;
}

function makePlayer() {
    let p = new Sprite();
    p.width = 45;
    p.height = 10;
    p.rotationLock = true;
    p.physics = KINEMATIC;
    p.visible = false;
    p.fill = 'red';
    p.bounciness = 1;
    p.friction = 0;

    return p;
}

function applySpawn(sprite, spawn) {
    
    sprite.physicsEnabled = true;
    sprite.visible = true;
    sprite.pos = {x: spawn.x, y: spawn.y };
    sprite.vel.x = 0;
    sprite.vel.y = 0;
}

function playerMovement() {
    if (kb.pressing('left') || contro.pressing('left')) {
        player0.vel.x = -PLAYER_SPEED;
        player0.vel.y = 0;
    } else if (kb.pressing('right') || contro.pressing('right')) {
        player0.vel.x = PLAYER_SPEED;
        player0.vel.y = 0;
    
    } else if (abs(contro.leftStick.x) > 0.2) {
        player0.vel.x = contro.leftStick.x * PLAYER_SPEED;
        player0.vel.y = 0;

    } else {
        player0.vel.x = 0;
        player0.vel.x = 0;
    }
}

function aiMovement() {
    if (!ballServed || ball.vel.y >= 0) {
        aiHasAim = false;
        player1.vel.x = 0;
        player1.vel.y = 0;
        return;
    }

    if (!aiHasAim) {
        rollAiAim()
    }

    let target = ball.x + aiAimOffset;
    let gap = target - player1.x;

    if (gap > 4) {
        player1.vel.x = PLAYER_SPEED;
    } else if (gap < -4) {
        player1.vel.x = -PLAYER_SPEED;
    } else {
        player1.vel.x = 0;
    }
    player0.vel.y = 0;
}

function rollAiAim() {
    aiHasAim = true;
    aiAimOffset = random(-AI_MAX_OFFSET, AI_MAX_OFFSET);
}

function resumeGame() {
    blockHover = 0;
    gameState = 'newGame';
    world.timeScale = 1;

    player0.visible = true;
    player0.fill = 'red';
    player0.strokeWeight = 2;

    player1.visible = true;
    player1.fill = 'cyan';
    player1.strokeWeight = 2;

    ball.visible = true;
    ball.fill = 'white';
    ball.strokeWeight = 2;
    for (let box of lives) {
        box.visible = true;
        box.stroke = box.taken ? 'white' : 'grey';
        box.strokeWeight = 2;
        box.fill = box.taken ? 'red' : color(0, 0, 0, 0);
    }

    for (let box of lives1) {
        box.visible = true;
        box.stroke = box.taken ? 'white' : 'grey';
        box.strokeWeight = 2;
        box.fill = box.taken ? 'red' : color(0, 0, 0, 0);
    }
    

    parkUI(continueBtn);
    parkUI(exitBtn);
    parkUI(settingsBtn);
}

function exitGame() {
    gameState = 'menu';
    world.timeScale = 1;

    player0.visible = false;
    player0.fill = color(0, 0, 0, 0);
    player0.stroke = color(0, 0, 0, 0);
    player0.strokeWeight = 0;

    player1.visible = false;
    player1.fill = color(0, 0, 0, 0);
    player1.stroke = color(0, 0, 0, 0);
    player1.strokeWeight = 0;

    ball.visible = false;
    ball.fill = color(0, 0, 0, 0);
    ball.stroke = color(0, 0, 0, 0);
    ball.strokeWeight = 0;

    parkUI(continueBtn);
    parkUI(exitBtn);

    placeUI(newGameBtn, 0, 30);
    placeUI(multiplayerBtn, 0, 100);
    placeUI(settingsBtn, 0, 170);
}

function pongBall() {
    let pb = new Sprite();
    pb.diameter = 15;
    pb.visible = false;
    pb.fill = 'white';
    pb.strokeWeight = 2;
    pb.stroke = 'red';
    pb.bounciness = 1;
    pb.friction = 0;

    return pb;
    
}

function bounceBallOnEdges() {
    let r = ball.diameter / 2;
    let left = -400 + r;
    let right = 400 - r;
    let top = -300 + r;
    let buttom = 300 - r;

    if (ball.x < left) {
        ball.x = left;
        ball.vel.x = abs(ball.vel.x);
    } else if (ball.x > right) {
        ball.x = right;
        ball.vel.x = -abs(ball.vel.x);
    }

    if (ball.y < top) {
        ball.y = top;
        ball.vel.y = abs(ball.vel.y);
    } else if (ball.y > buttom) {
        ball.y = buttom;
        ball.vel.y = -abs(ball.vel.y);
    }
}

function bounceBallOnPaddle() {
    bounceOffPaddle(player0, -1);
    bounceOffPaddle(player1, 1);
}

function bounceOffPaddle(paddle, ySign) {
    if (!ball.collides(paddle)) {
        return;
    }

    lastHitter = paddle;

    let hit = (ball.x - paddle.x) / (paddle.width / 2);
    hit = constrain(hit, -1, 1);

    ballSpeed += 0.1;
    ball.vel.x = hit * ballSpeed * 0.6;
    ball.vel.y = ySign * ballSpeed;
}

function ballMovement() {
    ball.vel.x = random() < 0.5 ? -ballSpeed: ballSpeed;
    ball.vel.y = -ballSpeed;
}

function makeLives(spawnY) {
    let lives = new Group();
    lives.w = LIFE_TILE;
    lives.h = LIFE_TILE;
    lives.tile = 'L';
    lives.fill = color(0, 0, 0, 0);
    lives.stroke = 'grey';
    lives.strokeWeight = 2;
    lives.physics = STATIC;
    lives.physicsEnabled = false;
    lives.visible = false;


    let row = 'L'.repeat(LIFE);
    let rowW = LIFE * LIFE_STRIDE;
    let x0 = -rowW / 2 + LIFE_STRIDE / 2;
    lives.addTiles([row], x0, spawnY, LIFE_STRIDE, LIFE_TILE);
    return lives;

}

function playerLifeCount() {
    ball.collides(lives, (b, box) => {
        if (box.taken) {
            return;
        }
        box.taken = true;
        box.fill = 'red';
        box.stroke = 'white';
        lifeCount += 1;

        if (lifeCount >= LIFE) {
            gameState = 'game-over';
            menuIndex = 0;
        } else {
            resetRally();
        }
    });   
    
    ball.collides(lives1, (b, box) => {
        if (box.taken) {
            return;
        }
        box.taken = true;
        box.fill = 'red';
        box.stroke = 'white';
        lifeCount += 1;

        if (lifeCount >= LIFE) {
            gameState = 'game-over';
            menuIndex = 0; 
        } else {
            resetRally();
        }
    });
}

function resetLivesRow(row) {
    for (let box of row) {
        box.visible = true;
        box.physicsEnabled = true;
        box.taken = false;
        box.fill = color(0, 0, 0, 0);
        box.stroke = 'grey';
        box.strokeWeight = 2;
    }
}

function resetLives() {
    resetLivesRow(lives);
    resetLivesRow(lives1);
    lifeCount = 0;
    lifeCount1 = 0;
}

//Boot
await Canvas(800, 600);
displayMode('maxed'); 
requestAnimationFrame(() => displayMode('maxed'));
world.gravity.y = GRAVITY;

let menuBG = await loadImage('./assets/mainMenu.jpeg');
let frostedBG = menuBG.copy();
frostedBG.filter(BLUR, 15);

let inGameBG = await loadImage('./assets/gameBackground.jpeg');
let titleImg = await loadImage('./assets/sprites/pong_rullet_title.png');
let newGameDefault = await loadImage('./assets/sprites/new_game_default.png');
let newGameSelected = await loadImage('./assets/sprites/new_game_selected.png');
let multiplayerDefault = await loadImage('./assets/sprites/multiplayer_default.png');
let multiplayerSelected = await loadImage('./assets/sprites/multiplayer_selected.png');
let settingsDefault = await loadImage('./assets/sprites/settings_default.png');
let settingsSelected = await loadImage('./assets/sprites/settings_selected.png');
let continueDefault = await loadImage('./assets/sprites/continue_default.png');
let continueSelected = await loadImage('./assets/sprites/continue_selected.png');
let exitDefault = await loadImage('./assets/sprites/exit_default.png');
let exitSelected = await loadImage('./assets/sprites/exit_selected.png');
let mainMenuDefault = await loadImage('./assets/sprites/mainMenuDefault.png');
let mainMenuSelected = await loadImage('./assets/sprites/mainMenuSelected.png');

//Sounds
let hoverSnd = await loadSound('./assets/sounds/hover.wav');
let selectedSnd = await loadSound('./assets/sounds/buttonSelected.wav');

let gameState = 'menu';
let settingsFrom = 'menu'; // bookmark
let ballServed = false;

//Widgets
let settingsWidget = makeWidget();

//Buttons
let newGameBtn = newGameButton();
newGameBtn.wasHovered = true;
let multiplayerBtn = multiplayerButton();
let settingsBtn = settingsButton();
let continueBtn = continueButton();
let exitBtn = exitButton();
let mainMenuBtn = backToMainMenuButton();


//GamePlay
let player0 = makePlayer();
let player1 = makePlayer();
player1.fill = 'cyan';
let ball = pongBall();
let lives = makeLives(LIFE_SPAWN.y);
let lives1 = makeLives(LIFE1_SPAWN.y);
let lifeCount = 0;
let lifeCount1 = 0;
let ballSpeed = BALL_SPEED; //We initialize the variable here so it can grow during a run
let lastHitter = player0;
let aiHasAim = false;
let aiAimOffset = 0;

//Update
q5.update = function() {
    pauseGame();

    if (gameState === 'menu') {
        showMenu();
    } else if (gameState === 'newGame') {
        background('black');
        imageMode(CENTER);
        image(inGameBG, 0, 0, 800, 600);
        playerMovement();
        aiMovement();
        if (!ballServed && player0.vel.x !==0) {
            ballMovement();
            ballServed = true;
        }
        bounceBallOnEdges();
        bounceBallOnPaddle();
        if (ballServed && ball.y < -300) {
            lastHitter = player0;
            resetRally();
        }
        playerLifeCount();
    

    } else if (gameState === 'paused') {
        background('black');
        imageMode(CENTER);
        image(inGameBG, 0, 0, 800, 600);
        pauseMenu()

    } else if (gameState === 'multiplayer') {
        multiplayer();

    } else if (gameState === 'settings') {
        settings();

    } else if (gameState === 'game-over') {
        gameOverMenu();
    }
}

q5.draw = function() {
};