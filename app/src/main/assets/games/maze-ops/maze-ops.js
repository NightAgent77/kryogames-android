// Maze Ops — solo maze + 2-player PeerJS race. You write this from scratch.

const SPAWN = {x: 0, y: 0};
const GRAVITY = 0;
const PLAYER_SPEED = 5;
const ROOM_W = 800;
const ROOM_H = 600;
const TILE = 25;
const x0 = -387.5;
const y0 = -287.5;
const RACE_W = 400;
const raceX0 = -187.5;
const raceY0 = y0;

const MAZE_0 = [
    '================================',
    '=+++++++++++++=+++++++++=+++++==',
    '=+===+=+===+===+===+=====+===+==',
    '=+++=+++=+++++++++=+=+++=+=+=+==',
    '=+=+=====+===+===+++++=+=+=+=+==',
    '=+=+++++++=+++++=+++++=+++++=+==',
    '=+=========+===+=+===+===+===+==',
    '=+++=+++++=+++=+++++=+++++++=+==',
    '===+===+=+===+=+=====+=====+=+==',
    '=+++++++=+++=+=+++++=+++=+++=+==',
    '=+====+======+=++==+===+=+===+==',
    '=+=+++++++++++++++=+=+++=+++++++',
    '=+=+===========+++=+++=====+=+==',
    '=+=+=+++++=+++++++=+=+++++=+++==',
    '===+=+=+===+=+==+==+=====+=+====',
    '=+++=+++=+++=+++++=+++=+++=+=+==',
    '=+===+=+=+===+===+===+=+===+=+==',
    '=+=+++=+=+=+++=+++++=+=+=+++++==',
    '=+===+=+=+===+=+===+=+=+=====+==',
    '=+=+++=+=+++++=+++=+++=+++=+=+==',
    '=+++===+=======+=+=======+=+=+==',
    '=+++=+++++++++++=+++++++++=+++==',
    '================================',
    '================================',
]

const MAZE_1 = [
    '================================',
    '=+++++++++++=+++++++++=+++=+++==',
    '=+=========+=+===+==++=+===+=+==',
    '=+=+++++++++=+=+=+=+++=+++++=+==',
    '=+===+==+====+=+=+===+=+=====+==',
    '=+++=+=+++++++++++++=+++++=+++==',
    '===+=+=+=======+===+=+=====+=+==',
    '=+++=+=+++++=+++++=+=+++++++=+==',
    '=+=====+=+=+===+=+=+=+==========',
    '=+=+++=+++=+++++=+++=+=+++++++==',
    '=+=+=+=====+=====+=+=+=+=====+==',
    '++++=+++++++=+++=+=+++=+=+++++==',
    '=+===+=======+===+=+===+=+======',
    '=+++++=+++=+=+=+++=+++=+=+++++==',
    '==+==+=+=+=+=+=+=======+=====+==',
    '=+++=+++++=+=+++++++++++=+++=+==',
    '=+=======+=+=+=========+===+=+==',
    '=+++++++=+=+=+++=+++++=+++++=+==',
    '=+=+===+=+=+=+=+=+===+=======+==',
    '=+=+++=+=+=+++=+=+++=+=+++++=+==',
    '=+===+===+===+=+==++=+=+=+++=+==',
    '=+++=+++++++++=+++++=+++=+++++==',
    '================+===============',
    '================+===============',
]

const MAZE_2 = [
    '================+===============',
    '=+++++++=+++++++++++=+++++++++==',
    '=+=+=+===+=======+===+=====+=+==',
    '=+=+=+=+++=+++=+=+=+++=+=+++=+==',
    '=+=+===+===+=+=+=+=+===+=+===+==',
    '=+++=+++=+++=+++++=+=+++=+=+++==',
    '=+===+===+=====+=+=+===+=+===+==',
    '=+++=+=+=+++++=+=+=+++++=+++=+==',
    '=+=+=+=+=+=+=+=+=+=====+===+====',
    '=+=+++=+++=+=+++=+++++++++=+++==',
    '=+=====+===+=+===+=========+++==',
    '=+=+=+++=+++=+=+=+++++=+++=+=+==',
    '=+=+=+===+===+=+=+===+=+=+===+==',
    '=+=+++++=+=+=+=+=+++=+=+++++++==',
    '=+=======+=+=+=+=+=+===+========',
    '=+++++=+++=+++=+++=+++++++++++==',
    '=+===+=+=====+=======+=+=+=+=+==',
    '=+=+++++++++=+=+++++++=+=+=+=+==',
    '=+=+===+=+=+=+=+=========+=+=+==',
    '=+++=+++=+=+=+=+++++++++++=+=+==',
    '=+=+=+++===+=+====+==++====+=+==',
    '=+++++=+++++=+++++++++++++++=+==',
    '===========================+====',
    '===========================+====',
]

const RACE_0 = [
    '================',
    '=+++++++++++++==',
    '===========+=+==',
    '=+++++++++++++==',
    '=+===+=++====+==',
    '=+++=+=+++++=+==',
    '=+=+=+=+===+=+==',
    '=+=+=+++=+=+=+==',
    '=+=+===+=+++=+==',
    '=+++++=+=+=+=+++',
    '=+===+=+++=+=+==',
    '=+=+++++++=+=+==',
    '=+=+===+++=+====',
    '=+=+++++++=+++==',
    '=+=====+=====+==',
    '=+++++++++++=+==',
    '===+=+====++=+==',
    '=+=+++++=+++=+==',
    '=+=+=====+=+=+==',
    '=+++=+++++=+=+==',
    '=++==+=======+==',
    '=+++=+++++++++==',
    '================',
    '================',
]

const RACE_1 = [
    '================',
    '=+++++++++++++==',
    '=+===+===+===+==',
    '=+++=+++++++=+==',
    '===+=======+=+==',
    '=+++++=+=+++=+==',
    '=====+=+=+=+====',
    '=+++=+++=+++++==',
    '=+=+=========+==',
    '++=+=+++=+++=+==',
    '=+=+++++=+=+=+==',
    '=+=+++=+=+=+++==',
    '=+=======+======',
    '=+++++++++=+++==',
    '=====+===+=+=+==',
    '=+++=+++=+++=+==',
    '=+=====+++===+==',
    '=+++++++++++=+==',
    '=+=======+===+==',
    '=+=+++++++=+++==',
    '=+===+=+++=+=+==',
    '=+++++++=+++=+==',
    '=======+========',
    '=======+========',
]

const RACE_2 = [
    '=======+========',
    '=+++=+++++++++==',
    '=+=+=+=======+==',
    '=+=+++++++=+++==',
    '=+=======+=+====',
    '=+++=+++++=+++==',
    '=====+=+=+===+==',
    '=+++++=+=+++++==',
    '=+=====+=+===+==',
    '=+=+++++++++++==',
    '=+===+=+=+===+==',
    '=+=+++=+=+++++==',
    '=+=+=+=+=+======',
    '=+++=+++=+++=+==',
    '=====+=====+=+==',
    '=+++++++++=+++==',
    '=+=+==+==+===+==',
    '=+=+++++=+++++==',
    '=+=+===+====+===',
    '=+=+++=+++++++==',
    '=+===========+==',
    '++++++++++++++==',
    '================',
    '================',
]

const RACE_3 = [
    '================',
    '=+++++++++++++==',
    '++=+===+========',
    '=+++=+=+++++++==',
    '=+===+=======+==',
    '=+++++++++=+++==',
    '=+==+==+=+=+====',
    '=+++++++=+=+++==',
    '=+=+===+=====+==',
    '=+=+=+++++++++==',
    '===+=+=======+==',
    '=+++++=+++=+++==',
    '=+=======+=+====',
    '=+=+++++++=+=+==',
    '=+=+=+===+=+++==',
    '=+++=+++++=+++==',
    '=+===+=+====++==',
    '=+++=+=+++=+++==',
    '===+++===+======',
    '=+=+=+++=+++++==',
    '=+=+===+=====+==',
    '=+++++=+++++++++',
    '================',
    '================',
]

const RACE_4 = [
    '================',
    '=+++++++=+++++==',
    '=+=+===+===+=+++',
    '=+=+++=+++++=+==',
    '=+===+=======+==',
    '=+++++++=+=+++==',
    '=======+=+=+====',
    '=+++++++++++=+==',
    '=+==+====+===+==',
    '=+++++=+++=+++==',
    '=+=+=+===+=+=+==',
    '=+++=+=+=+++=+==',
    '=+=+++=+=====+==',
    '=+=+=+++=+++++==',
    '=+++=====+=++===',
    '=+=+++++++=+++==',
    '=+===+=+=+======',
    '=+++=+++=+++++==',
    '=+=+=========+==',
    '=+=+++++++=+++==',
    '=+=====+===+====',
    '=+++++=+++++++==',
    '=+==============',
    '=+==============',
]

const RACE_5 = [
    '=+==============',
    '=+++=+++=+++++==',
    '=+=+=+=+=+=+====',
    '=+=+=+=+=+=+++==',
    '=+=+=+=+=====+==',
    '=+=+=+=+=+++++==',
    '=+===+=+=+===+==',
    '=+=+++=+=+++++==',
    '=+=+===+=+=+====',
    '=+++=+++=+++++==',
    '=+=+=+=+=+===+==',
    '=+=+=+++=+=+=+==',
    '=+=+===+=+=+=+==',
    '=+=+++++++=+++==',
    '=+=========+====',
    '=+++++++++=+++==',
    '=+=+=========+==',
    '=+=+++++++++++==',
    '=+=====+==+=====',
    '=+=+++++++++=+==',
    '=+=+==++=+=+=+==',
    '=+++=+++++++++==',
    '========+=======',
    '========+=======',
]

function showMenu() {
    background('#333');
    textAlign(CENTER, CENTER);
    textSize(50);
    fill('#fff')
    text('MAZE OPS', 0, -100);

    returnBtn.visible = false;
    returnBtn.physicsEnabled = false;

    soloWalls.visible = false;
    soloWalls.physicsEnabled = false;
    raceWalls.visible = false;
    raceWalls.physicsEnabled = false;

    player.visible = false;
    player.physicsEnabled = false;
    p2.visible = false;
    p2.physicsEnabled = false;

    newGameBtn.visible = true;
    newGameBtn.physicsEnabled = true;

    hostBtn.visible = true;
    hostBtn.physicsEnabled = true;

    startRaceBtn.visible = false;
    startRaceBtn.physicsEnabled = false;

    joinBtn.visible = true;
    joinBtn.physicsEnabled = true;

    let canClick = frameCount > 30;
        if (canClick && pointer.overlapping(newGameBtn)) {
            if (mouse.presses()) {
                startGame();
            }
        } else if (canClick && pointer.overlapping(hostBtn)) {
            if (mouse.presses()) {
                if (!hostPeer) {
                    let roomId = String(Math.floor(1000 + Math.random() * 9000));
                    hostPeer = new Peer(roomId);
                    hostPeer.on('open', (id) => {
                        hostId = id;
                    });

                    hostPeer.on('connection', (conn) => {
                        hostConn = conn;     
                        conn.on('open', () => {
                            hostConnected = true;
                        });
                        conn.on('data', (msg) => {
                            if(msg && msg.type === 'finished') {
                                raceWinner = msg.winner;
                                gameState = 'end';
                            } else {
                            applyGhost(msg);
                            }
                        });
                    });
                }
                gameState = 'host';
            }
                
        } else if (canClick && pointer.overlapping(joinBtn)) {
            if (mouse.presses()) {
                if (!joinPeer) {
                    joinPeer = new Peer();
                    joinPeer.on('open', () => {
                        joinReady = true;
                    });
                    joinPeer.on('error', (err) => {
                        if (err && err.type === 'peer-unavailable') {
                            joinConn = null;
                            joinConnected = false;
                        }
                    });
                }
                gameState = 'join';
            }
        }
}

function hostState() {
    background('#333');
    textAlign(CENTER, CENTER);
    textSize(50);
    fill('#fff');
    text('HOSTING', 0, -100);

    textAlign(CENTER, CENTER);
    textSize(18);
    fill('#fff');
    text('SERVER ID :', -20, -50); //Server ID number

    textAlign(CENTER, CENTER);
    textSize(18);
    text(hostId, 60, -50);

    textAlign(CENTER, CENTER);
    textSize(12);

    if (hostConnected) { //Will display if a user is connected or not
        text('CONNECTED', 0, 0);
        startRaceBtn.visible = true;
        startRaceBtn.physicsEnabled = true;
        startRaceBtn.x = 0;
        startRaceBtn.y = 90;
        startRaceClicklDelay++;

    } else {
        text('WAITING FOR PLAYER', 0, 0);
        startRaceBtn.visible = false;
        startRaceBtn.physicsEnabled = false;
        startRaceClicklDelay = 0;
    }
    

    newGameBtn.visible = false;
    newGameBtn.physicsEnabled = false;
    hostBtn.visible = false;
    hostBtn.physicsEnabled = false;
    joinBtn.visible = false;
    joinBtn.physicsEnabled = false;

    returnBtn.visible = true;
    returnBtn.physicsEnabled = true;
    returnBtn.x = 0;
    returnBtn.y = 200;

    let canClick = frameCount > 30;
    if (canClick && pointer.overlapping(returnBtn)) {
        if (mouse.presses()) {
            gameState = 'menu';
        }
    } else if (canClick && hostConnected && startRaceClicklDelay > 20 && pointer.overlapping(startRaceBtn)) {
        if (mouse.presses()) {
            myRole = 'p1';
            startRace();
            if (hostConn && hostConn.open) {
                hostConn.send({ type: 'start'});
            }
        }
    }
}

function joinState() {
    background('#333');

    textAlign(CENTER, CENTER);
    textSize(50);
    fill('#fff')
    text('JOIN LOBBY', 0, -100);

    textAlign(CENTER, CENTER);
    textSize(18);
    fill('#fff');
    text('ENTER SERVER ID : ', 0, -10);
    text(joinCode, 0, 20);

    newGameBtn.visible = false;
    newGameBtn.physicsEnabled = false;

    hostBtn.visible = false;
    hostBtn.physicsEnabled = false;

    joinBtn.visible = false;
    joinBtn.physicsEnabled = false;

    startRaceBtn.visible = false;
    startRaceBtn.physicsEnabled = false;    

    returnBtn.visible = true;
    returnBtn.physicsEnabled = true;
    returnBtn.x = 0;
    returnBtn.y = 200;
    
    if (kb.presses('0')) {
        joinCode += '0';
    } else if (kb.presses('1')) {
        joinCode += '1';
    } else if (kb.presses('2')) {
        joinCode += '2';
    } else if (kb.presses('3')) {
        joinCode += '3';
    } else if (kb.presses('4')) {
        joinCode += '4';
    } else if (kb.presses('5')) {
        joinCode += '5';
    } else if (kb.presses('6')) {
        joinCode += '6';
    } else if (kb.presses('7')) {
        joinCode += '7';
    } else if (kb.presses('8')) {
        joinCode += '8';
    } else if (kb.presses('9')) {
        joinCode += '9';
    } else if (kb.presses('backspace')) {
        joinCode = joinCode.slice(0, -1);
    } else if (kb.presses('enter')) {
        if (joinReady && joinCode !== '' && joinConn === null) {
            joinConn = joinPeer.connect(joinCode);
            joinConn.on('open', () => {
                joinConnected = true;
            });
            joinConn.on('data', (msg) => {
                if (msg && msg.type === 'start') {
                    myRole = 'p2';
                    startRace();
                } else if (msg && msg.type === 'finished') {
                    raceWinner = msg.winner;
                    gameState = 'end';
                } else {
                    applyGhost(msg);
                }
            });
            }
        }
    

    textSize(12);
    fill('#fff');
    if (joinConnected) {
        
        text('CONNECTED', 0, 60);

    } else {
        text('WAITING', 0, 60);
    }

    let canClick = frameCount > 30;
    if (canClick && pointer.overlapping(returnBtn)) {
        if (mouse.presses()) {
            gameState = 'menu';
        }
    }
}

function startGame() {
    myRole = 'p1';
    playMode = 'solo';
    raceWinner = '';

    newGameBtn.visible = false;
    newGameBtn.physicsEnabled = false;

    hostBtn.visible = false;
    hostBtn.physicsEnabled = false;

    startRaceBtn.visible = false;
    startRaceBtn.physicsEnabled = false;

    joinBtn.visible = false;
    joinBtn.physicsEnabled = false;
    
    returnBtn.visible = false;
    returnBtn.physicsEnabled = false;

    player.physics = DYNAMIC;
    player.visible = true;
    player.physicsEnabled = true;
    applyP1Spawn();

    p2.visible = false;
    p2.physicsEnabled = false;

    soloWalls.visible = true;
    soloWalls.physicsEnabled = true;
    raceWalls.visible = false;
    raceWalls.physicsEnabled = false;

    gameState = 'playing';
}

function startRace() {
    playMode = 'multi';
    raceWinner = '';
    player.pass(p2);
    if (myRole === 'p2') {
        player.physics = KINEMATIC;
        p2.physics = DYNAMIC;
    } else {
        player.physics = DYNAMIC;
        p2.physics = KINEMATIC;
    }

    player.visible = true;
    player.physicsEnabled = true;

    p2.visible = true;
    p2.physicsEnabled = true;
    applyP1Spawn();
    applyP2Spawn();

    newGameBtn.visible = false;
    newGameBtn.physicsEnabled = false;

    hostBtn.visible = false;
    hostBtn.physicsEnabled = false;

    startRaceBtn.visible = false;
    startRaceBtn.physicsEnabled = false;

    joinBtn.visible = false;
    joinBtn.physicsEnabled = false;

    returnBtn.visible = false;
    returnBtn.physicsEnabled = false;

    soloWalls.visible = false;
    soloWalls.physicsEnabled = false;
    raceWalls.visible = true;
    raceWalls.physicsEnabled = true;

    gameState = 'playing';
}

function playState() {
    background('white');
    
    if (playMode === 'solo') {
        soloWalls.visible = true;
        raceWalls.visible = false;
        
    } else {
        soloWalls.visible = false;
        raceWalls.visible = true;
    }

    playerMovement();

    let rw = playMode === 'multi' ? RACE_W : ROOM_W;
    let leftEdge = -rw / 2;
    let topEdge = -ROOM_H / 2;

    p1RoomCol = Math.floor((player.x - leftEdge) / rw);
    p1RoomRow = Math.floor((player.y - topEdge) / ROOM_H);
    p2RoomCol = Math.floor((p2.x - leftEdge) / rw);
    p2RoomRow = Math.floor((p2.y - topEdge) / ROOM_H);

    if (myRole === 'p2') {
        roomCol = p2RoomCol;
        roomRow = p2RoomRow;
    } else {
        roomCol = p1RoomCol;
        roomRow = p1RoomRow;
    }

    if (playMode === 'multi') {
        if (roomCol === -1 && roomRow >= 3) {
            if (!raceWinner) {
                raceWinner = myRole;
                if (hostConn && hostConn.open) {
                    hostConn.send({ type: 'finished', winner: myRole });
                 }  else if (joinConn && joinConn.open) {
                    joinConn.send({ type: 'finished', winner: myRole });
                 }
            }
            gameState = 'end';
        }
    } else if (roomCol === 1 && roomRow >= 2) {
        gameState = 'end';
    }

    if (playMode === 'multi') {
        sendMyPos();
    }
}

function showEndView() {
    background('#333');
    player.visible = false;
    p2.visible = false;

    soloWalls.visible = false;
    soloWalls.physicsEnabled = false;
    raceWalls.visible = false;
    raceWalls.physicsEnabled = false;

    returnBtn.visible = true;
    returnBtn.physicsEnabled = true;

    startRaceBtn.visible = false;
    startRaceBtn.physicsEnabled = false;

    textAlign(CENTER);
    textSize(50);
    fill('white');

    if (raceWinner === 'p1') {
        text('P1 wins', 0, -40);
    } else if (raceWinner === 'p2') {
        text('P2 wins', 0, -40);
    } else {
        text('You Escaped!', 0, -40);
    }

    let canClick = frameCount > 30;
    if (pointer.overlapping(returnBtn)) {
        if (mouse.presses()) {
            gameState = 'menu';
            raceWinner = '';
            roomCol = 0;
            roomRow = 0;
            p1RoomCol = 0;
            p1RoomRow = 0;
            p2RoomCol = 0;
            p2RoomRow = 0;
            returnBtn.visible = false;
            newGameBtn.physicsEnabled = true;
            hostBtn.physicsEnabled = true;
            joinBtn.physicsEnabled = true;
        }
    }
}

function newGameButton() {
    let ngBtn = new Sprite();
    ngBtn.layer = 10;
    ngBtn.width = 160;
    ngBtn.height = 60;
    ngBtn.x = 0;
    ngBtn.y = 0;
    ngBtn.physics = STATIC;

    ngBtn.draw = function() {
        rectMode(CENTER);
        noStroke();

        if (pointer.overlapping(this)) {
            fill('#e63950');
        } else {
            fill('#DC143C');
        }

        rect(0, 0, this.width, this.height);
        textAlign(CENTER, CENTER);
        textSize(20);
        fill('black');
        text('New Game', 0, 2);
    };
    return ngBtn;
}

function startRaceButton() {
    let sBtn = new Sprite();
    sBtn.layer = 10;
    sBtn.width = 160;
    sBtn.height = 60;
    sBtn.x = 0;
    sBtn.y = 90;
    sBtn.physics = STATIC;
    sBtn.visible = false;
    sBtn.physicsEnabled = false;
    

    sBtn.draw = function() {
        rectMode(CENTER);
        noStroke();

        if (pointer.overlapping(this)) {
            fill('#e63950');
        } else {
            fill('#DC143C');
        }

        rect(0, 0, this.width, this.height);
        textAlign(CENTER, CENTER);
        textSize(20);
        fill('black');
        text('Start Race', 0, 2);
    };

    return sBtn;
}

function hostButton() {
    let hBtn = new Sprite();
    hBtn.width = 160;
    hBtn.height = 60;
    hBtn.x = 0;
    hBtn.y = 70;
    hBtn.physics = STATIC;

    hBtn.draw = function() {
        rectMode(CENTER);
        noStroke();

        if (pointer.overlapping(this)) {
            fill('#e63950');
        } else {
            fill('#DC143C');
        }

        rect(0, 0, this.width, this.height);
        textAlign(CENTER, CENTER);
        textSize(20);
        fill('black');
        text('Host', 0, 2);
    };
    return hBtn;
}

function joinButton() {
    let jBtn = new Sprite();
    jBtn.width = 160;
    jBtn.height = 60;
    jBtn.x = 0;
    jBtn.y = 140;
    jBtn.physics = STATIC;

    jBtn.draw = function() {
        rectMode(CENTER);
        noStroke();

        if (pointer.overlapping(this)) {
            fill('#e63950');
        } else {
            fill('#DC143C');
        }

        rect(0, 0, this.width, this.height);
        textAlign(CENTER, CENTER);
        textSize(20);
        fill('black');
        text('Join', 0, 2);
    };
    return jBtn;
}

function returnButton() {
    let rBtn = new Sprite();
    rBtn.width = 160;
    rBtn.height = 60;
    rBtn.x = 0;
    rBtn.y = 40;
    rBtn.physics = STATIC;

    rBtn.draw = function() {
        rectMode(CENTER);
        noStroke();

        if (pointer.overlapping(this)) {
            fill('#e63950');
        } else {
            fill('#DC143C');
        }

        rect(0, 0, this.width, this.height);
        textAlign(CENTER, CENTER);
        textSize(20);
        fill('black');
        text('Main Menu', 0, 2);
    }
    return rBtn;
}

function makePlayer(fillColor) {
    let p = new Sprite();
    p.width = 17;
    p.height = 17;
    p.fill = fillColor; //uses 'blue' or 'orange' from the caller -> (let player = makePlayer('blue'))
    p.rotationLock = true;
    p.visible = false;

    p.draw = function() {
        rectMode(CENTER);
        noStroke();
        fill(this.fill);
        rect(0,0, this.width, this.height);
    };

    return p;
}

function applyP1Spawn() {
    player.physicsEnabled = true;
    player.pos = { x: SPAWN.x, y: SPAWN.y };
    player.vel.x = 0;
    player.vel.y = 0;
}

function applyP2Spawn() {
    p2.physicsEnabled = true;
    p2.pos = { x: SPAWN.x, y: SPAWN.y };
    p2.vel.x = 0;
    p2.vel.y = 0;
}

function applyGhost(msg) {
    if (!msg || msg.type !== 'pos') return;
    let ghost = myRole === 'p2' ? player : p2;
    ghost.x = msg.x;
    ghost.y = msg.y;
    ghost.vel.x = 0;
    ghost.vel.y = 0;
}

function sendMyPos() {
    let me = myRole === 'p2' ? p2 : player;
    let payload = { type: 'pos', x: me.x, y: me.y };
    if (hostConn && hostConn.open) {
        hostConn.send(payload);
    } else if (joinConn && joinConn.open) {
        joinConn.send(payload);
    }
}

function playerMovement() {
    let me = myRole === 'p2' ? p2 : player;

    if (kb.pressing('left')) {
        me.vel.x = -PLAYER_SPEED;
    } else if (kb.pressing('right')) {
        me.vel.x = PLAYER_SPEED;
    } else {
        me.vel.x = 0;
    }

    if (kb.pressing('up')) {
        me.vel.y = -PLAYER_SPEED;
    } else if (kb.pressing('down')) {
        me.vel.y = PLAYER_SPEED;
    } else {
        me.vel.y = 0;
    }
}

function makeWalls(map0, map1, map2, originX, originY, roomW, map3, map4, map5) {
    let w = new Group();
    w.tile = '=';
    w.width = TILE;
    w.height = TILE;
    w.physics = STATIC;
    w.fill = 'red';
    w.addTiles(map0, originX, originY, TILE, TILE);
    w.addTiles(map1, originX + roomW, originY, TILE, TILE);
    w.addTiles(map2, originX + roomW, originY + ROOM_H, TILE, TILE);
    if (map3) {
        w.addTiles(map3, originX, originY + ROOM_H, TILE, TILE);
        w.addTiles(map4, originX - roomW, originY + ROOM_H, TILE, TILE);
        w.addTiles(map5, originX - roomW, originY + 2 * ROOM_H, TILE, TILE);
    }

    for (let tile of w) {
        tile.draw = function () {
            rectMode(CENTER);
            noStroke();
            fill(this.fill);
            rect(0, 0, this.width, this.height);
        }
    }
    w.visible = false;
    w.physicsEnabled = false;

    return w;
}

function drawPanel(followX, followY, panelCenterX, spr) {
    let leftBound = panelCenterX - 200;
    let rightBound = panelCenterX + 200;

    for (let tile of raceWalls) {
        let sx = tile.x - followX + panelCenterX;
        let sy = tile.y - followY;
        if (sx < leftBound || sx > rightBound) continue;
        if (sy < -300 || sy > 300) continue;
        rectMode(CENTER);
        noStroke();
        fill(tile.fill);
        rect(sx, sy, tile.width, tile.height);
        
    }

    
    let sx = spr.x - followX + panelCenterX;
    let sy = spr.y - followY;
    if (sx < leftBound - 25 || sx > rightBound + 25) return;
    rectMode(CENTER);
    noStroke();
    fill(spr.fill);
    rect(sx, sy, spr.width, spr.height);
    
}

function panelFollowX(px, col) {
    let mid = col * ROOM_W;
    return Math.max(mid - 200, Math.min(px, mid + 200));
}

function drawRaceWorld() {
    background('white');
    raceWalls.autoDraw = true;
    player.autoDraw = true;
    p2.autoDraw = true;
    camera.on();
    raceWalls.draw();
    player.draw();
    p2.draw();
}

//Boot
await Canvas(800, 600);
displayMode('maxed'); //Display adjusts to the size of the window
requestAnimationFrame(() => displayMode('maxed'));
world.gravity.y = GRAVITY;


let hostPeer = null;
let hostId = '';
let hostConn = null;
let myRole = 'p1';
let hostConnected = false;
let startRaceClicklDelay = 0;

let joinPeer = null;
let joinCode = '';
let joinReady = false;
let joinConn = null;
let joinConnected = false;

let gameState = 'menu'
let newGameBtn = newGameButton();
let startRaceBtn = startRaceButton();
let hostBtn = hostButton();
let joinBtn = joinButton();
let returnBtn = returnButton();

let player = makePlayer('blue');
let p2 = makePlayer('orange');

let roomCol = 0;
let roomRow = 0;
let p1RoomCol = 0;
let p1RoomRow = 0;
let p2RoomCol = 0;
let p2RoomRow = 0;
let playMode = 'solo';
let raceWinner = '';
let soloWalls = makeWalls(MAZE_0, MAZE_1, MAZE_2, x0, y0, ROOM_W);
let raceWalls = makeWalls(RACE_0, RACE_1, RACE_2, raceX0, raceY0, RACE_W, RACE_3, RACE_4, RACE_5);

//Update
q5.update = function() {
    if (gameState === 'menu') {
        showMenu();

    } else if (gameState === 'host') {
        hostState();
        
    } else if (gameState === 'join') {
        joinState();

    } else if (gameState === 'playing') {
        playState();
        
    } else if (gameState === 'end') {
        showEndView();
    }
};

q5.draw = function() {
    if (gameState === 'menu') {
        allSprites.autoDraw = true;
        camera.x = 0;
        camera.y = 0;

    } else if (gameState === 'host') {
        allSprites.autoDraw = true;
        camera.x = 0;
        camera.y = 0;

    } else if (gameState === 'join') {
        allSprites.autoDraw = true;
        camera.x = 0;
        camera.y = 0;
       
    } else if (gameState === 'playing') {
        if (playMode !== 'multi') {
            allSprites.autoDraw = true;
            player.autoDraw = true;
            p2.autoDraw = true;
            camera.x = roomCol * ROOM_W;
            camera.y = roomRow * ROOM_H;

        } else {
            allSprites.autoDraw = true;
            player.autoDraw = false;
            p2.autoDraw = false;
            raceWalls.autoDraw = false;
            
            camera.off();
            background('white');

            drawPanel(
                p1RoomCol * RACE_W,
                p1RoomRow * ROOM_H,
                -200,
                player
            );
            drawPanel(
                p2RoomCol * RACE_W,
                p2RoomRow * ROOM_H,
                200,
                p2
            );

            stroke('#000');
            strokeWeight(2);
            line(0, -300, 0, 300);

            noStroke();
            textAlign(CENTER, CENTER);
            textSize(18);
            fill('#000');
            text('P1', -200, -280);
            text('P2', 200, -280);
            let youX = myRole === 'p2' ? 200 : -200;
            text('YOU', youX, -255);
        }

    } else if (gameState === 'end') {
        allSprites.autoDraw = true;
        camera.x = 0;
        camera.y = 0;
    }
};