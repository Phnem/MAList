from PIL import Image
from pathlib import Path


def remove_black_bg(path: Path, threshold: int = 22) -> None:
    img = Image.open(path).convert("RGBA")
    w, h = img.size
    pixels = img.load()

    def is_dark(x: int, y: int) -> bool:
        r, g, b, _a = pixels[x, y]
        return r <= threshold and g <= threshold and b <= threshold

    bg = [[False] * w for _ in range(h)]
    stack: list[tuple[int, int]] = []
    for x in range(w):
        for y in (0, h - 1):
            if is_dark(x, y) and not bg[y][x]:
                bg[y][x] = True
                stack.append((x, y))
    for y in range(h):
        for x in (0, w - 1):
            if is_dark(x, y) and not bg[y][x]:
                bg[y][x] = True
                stack.append((x, y))

    while stack:
        x, y = stack.pop()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and not bg[ny][nx] and is_dark(nx, ny):
                bg[ny][nx] = True
                stack.append((nx, ny))

    for y in range(h):
        for x in range(w):
            if bg[y][x]:
                r, g, b, _a = pixels[x, y]
                pixels[x, y] = (r, g, b, 0)

    bbox = img.getbbox()
    if bbox:
        img = img.crop(bbox)

    img.save(path)
    print(f"processed {path} ({img.size[0]}x{img.size[1]})")


if __name__ == "__main__":
    store = Path(__file__).resolve().parents[1] / "images" / "store"
    for name in ("github-store.png", "fdroid.png", "obtainium.png"):
        remove_black_bg(store / name)
