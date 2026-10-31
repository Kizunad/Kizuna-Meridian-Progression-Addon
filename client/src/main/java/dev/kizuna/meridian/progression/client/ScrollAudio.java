package dev.kizuna.meridian.progression.client;

import dev.kizuna.meridian.progression.ScrollContent;
import dev.kizuna.meridian.progression.ScrollPlayback;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** 一次阅读持有自己的声音实例；纸声只触发一次，低风随卷轴淡入淡出。 */
final class ScrollAudio {
    private static final float PAPER_VOLUME = 0.22f;
    private static final float WIND_VOLUME = 0.05f;
    private final SoundManager sounds;
    private final ScrollSound opening = new ScrollSound("paper_rustle", PAPER_VOLUME, false);
    private final ScrollSound closing = new ScrollSound("paper_close", PAPER_VOLUME, false);
    private final ScrollSound wind = new ScrollSound("low_wind", 0, true);
    private boolean started;
    private boolean closingPlayed;
    private boolean stopped;

    ScrollAudio(SoundManager sounds) {
        // 阅读授权、计时和屏幕生命周期仍由界面管理，这里只管理声音。
        this.sounds = sounds;
    }

    void start() {
        // resize 会再次 init；同一会话不得重播展开声或叠加第二条风声。
        if (started || stopped) {
            return;
        }
        started = true;
        sounds.play(opening);
        sounds.play(wind);
    }

    void update(ScrollPlayback playback) {
        // 仅在进入合拢阶段时触发一次；大帧跨过结尾时直接清理，不补播过期声音。
        if (!started || stopped) {
            return;
        }
        if (playback.finished()) {
            stop();
            return;
        }
        double closingStartsMs = playback.totalMs() - ScrollContent.CLOSING_MS;
        if (!closingPlayed && playback.elapsedMs() >= closingStartsMs) {
            closingPlayed = true;
            sounds.play(closing);
        }
        wind.level(WIND_VOLUME * playback.openness());
    }

    void stop() {
        // 中途取消不播放合拢声，立即释放这次阅读的全部声音，避免切屏后残留。
        if (stopped) {
            return;
        }
        stopped = true;
        sounds.stop(opening);
        sounds.stop(closing);
        sounds.stop(wind);
    }

    private static final class ScrollSound extends AbstractTickableSoundInstance {
        ScrollSound(String name, float volume, boolean loop) {
            super(SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("meridian_progression", name)), SoundSource.AMBIENT, RandomSource.create());
            // 屏幕声音跟随听者，仅风声循环，避免场景位置和距离影响阅读音量。
            this.volume = volume;
            looping = loop;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
        }

        void level(float volume) {
            // 音量来自卷轴实际开合程度，不给正文叠加周期性音量调制。
            this.volume = volume;
        }

        @Override
        public boolean canStartSilent() {
            // 风声从零音量淡入，允许声音引擎在初始静音时创建声源。
            return looping;
        }

        @Override
        public void tick() {
            // SoundEngine 每 tick 应用当前音量；界面移除时显式停止实例。
        }
    }
}
