package io.github.capsopasme.tuya.game;

/** 音效事件编号（游戏逻辑只发事件，播放由 app 层负责）。 */
public final class Snd {
    private Snd() {}

    public static final int JUMP = 0, SPRING = 1, TRAMP = 2, BREAK = 3, SHOOT = 4, HIT = 5, MDIE = 6, STOMP = 7,
            PICKUP = 8, PROP = 9, JET = 10, WINGS = 11, DARKWINGS = 12, DARKFLAP = 13, FLAP = 14, SMASH = 15,
            FALL = 16, HOLE = 17, ABDUCT = 18, OVER = 19, CLICK = 20,
            LIFE = 21, REVIVE = 22, LUCK = 23, DRAGON = 24, DRAGONFLAP = 25, FIREBALL = 26, BOOM = 27,
            CLOAK = 28, WHOOSH = 29;
    public static final int COUNT = 30;
}
