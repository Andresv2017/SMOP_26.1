#!/usr/bin/env python3
"""Construye el sheet de dos filas de la barra de habilidades a partir del arte de una sola fila.

Por qué existe: el arte se autorea como UNA barra encendida (marco + tira roja segmentada). El HUD
necesita dos estados alineados píxel a píxel — el marco con la tira apagada, y la tira encendida
suelta — porque recorta el relleno con las mismas coordenadas que el marco. Separarlos a mano cada
vez que se retoca el arte es donde se cuela el desalineo de un píxel que nadie ve hasta que la barra
va por la mitad.

El sheet sale en escala de grises a propósito: el HUD multiplica cada fila por un color, y una
fuente roja pura (R, 0, 0) no se puede teñir de morado por multiplicación. Con gris, el tinte
0xFFFF0000 devuelve exactamente el arte original.

    python tools/build-ability-bar.py

Imprime las constantes del canal interior para pegarlas en RiderAbilityHud.
"""

import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "tools", "art", "rider_ability_bar_source.png")
TARGET = os.path.join(ROOT, "src", "main", "resources", "assets", "smop",
                      "textures", "gui", "rider_ability_bar.png")

SHEET_WIDTH = 256
SHEET_HEIGHT = 64

# La tira encendida vive en estas filas del arte. Fuera de ellas está el marco: los remates de los
# extremos y la calavera central, que nunca se recortan.
STRIP_ROWS = range(11, 14)
# La fila del medio es la que se usa para localizar el adorno central: es la única donde la silueta
# de la calavera corta la tira limpia. En la de abajo asoman los dientes, encendidos, y partirían el
# hueco en trozos de dos píxeles indistinguibles de un separador.
SKULL_PROBE_ROW = 12
# Los separadores entre segmentos miden 1 px. Cualquier hueco más ancho es el adorno.
MIN_SKULL_WIDTH = 3
# Un píxel de la tira cuenta como "encendido" a partir de aquí. Los separadores entre segmentos y el
# contorno se quedan por debajo (llegan a 67), y los segmentos empiezan en 100.
LIT_THRESHOLD = 90
# Cuánto queda de la tira cuando la habilidad está gastada. Es el único número de gusto del script.
SPENT_FACTOR = 0.22


def main():
    art = Image.open(SOURCE).convert("RGBA")
    width, height = art.size
    px = art.load()

    def lit(x, y):
        return px[x, y][3] > 0 and px[x, y][0] >= LIT_THRESHOLD

    lit_columns = {x for x in range(width) for y in STRIP_ROWS if lit(x, y)}
    left, right = min(lit_columns), max(lit_columns)

    # El hueco de la calavera parte la tira en dos; sus píxeles pertenecen al marco, no al relleno,
    # o el adorno central se apagaría con la recarga.
    skull = range(0, 0)
    start = None
    for x in range(left, right + 2):
        if x <= right and not lit(x, SKULL_PROBE_ROW):
            start = x if start is None else start
        elif start is not None:
            if x - start >= MIN_SKULL_WIDTH and x - start > len(skull):
                skull = range(start, x)
            start = None

    def is_fill(x, y):
        return y in STRIP_ROWS and x not in skull and lit(x, y)

    sheet = Image.new("RGBA", (SHEET_WIDTH, SHEET_HEIGHT), (0, 0, 0, 0))
    out = sheet.load()
    for y in range(height):
        for x in range(width):
            r, _, _, a = px[x, y]
            if a == 0:
                continue
            fill = is_fill(x, y)
            frame = int(round(r * SPENT_FACTOR)) if fill else r
            out[x, y] = (frame, frame, frame, a)
            if fill:
                out[x, y + height] = (r, r, r, a)

    sheet.save(TARGET)

    inner_rows = [y for y in STRIP_ROWS if any(is_fill(x, y) for x in range(width))]
    print("BAR_WIDTH   = %d" % width)
    print("BAR_HEIGHT  = %d" % height)
    print("INNER_X     = %d" % left)
    print("INNER_Y     = %d" % min(inner_rows))
    print("INNER_WIDTH = %d" % (right - left + 1))
    print("INNER_HEIGHT= %d" % (max(inner_rows) - min(inner_rows) + 1))
    print("hueco de la calavera: x %d..%d" % (skull.start, skull.stop - 1) if skull else "sin hueco")
    print("escrito -> %s" % TARGET)


if __name__ == "__main__":
    main()
