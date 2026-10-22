"""生成自制占位音效；不采样第三方录音。需要本机 ffmpeg/libvorbis。"""
from array import array
import math
from pathlib import Path
import random
import subprocess
import tempfile
import wave

ROOT = Path(__file__).resolve().parents[1]
RATE = 22050


def paper(duration, seed):
    # 一次翻动只保留一个柔和包络，首尾归零，不再把展开声合成为循环素材。
    rng = random.Random(seed)
    smooth = 0.0
    samples = []
    count = round(RATE * duration)
    for index in range(count):
        noise = rng.uniform(-1, 1)
        smooth = smooth * 0.65 + noise * 0.35
        envelope = math.sin(math.pi * index / (count - 1)) ** 1.6
        samples.append((noise - smooth) * envelope * 0.25)
    return samples


def wind(duration, seed):
    # 对同一圈噪声预热滤波器，再输出第二圈，首尾具有相同滤波状态。
    # 不叠加正弦音量包络，不用首尾淡出造成每圈一顿；开合淡入淡出由界面控制。
    rng = random.Random(seed)
    noise = [rng.uniform(-1, 1) for _ in range(round(RATE * duration))]
    low = 0.0
    slow = 0.0
    samples = []
    for turn in range(2):
        for value in noise:
            low = low * 0.945 + value * 0.055
            slow = slow * 0.994 + low * 0.006
            if turn == 1:
                samples.append(low - slow)
    # 整段使用固定增益保留自然细碎变化，避开可辨认的低频周期起伏。
    rms = math.sqrt(sum(value * value for value in samples) / len(samples))
    return [value * 0.026 / rms for value in samples]


def write_ogg(name, samples):
    # 以固定采样率和编码参数生成原创占位素材，临时 WAV 不进入仓库。
    target = ROOT / "client/src/main/resources/assets/meridian_progression/sounds" / (name + ".ogg")
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="progression-audio-") as directory:
        source = Path(directory) / "sound.wav"
        with wave.open(str(source), "wb") as output:
            output.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
            output.writeframes(array("h", [int(max(-1, min(1, value)) * 32767) for value in samples]).tobytes())
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(source),
                        "-c:a", "libvorbis", "-q:a", "3", "-map_metadata", "-1", str(target)], check=True)
    print(target.relative_to(ROOT))


if __name__ == "__main__":
    # 两段纸声短于四倍速的展开/合拢时间，因此无需重播、变调或截断。
    write_ogg("paper_rustle", paper(0.28, 7341))
    write_ogg("paper_close", paper(0.24, 7343))
    write_ogg("low_wind", wind(12, 7342))
