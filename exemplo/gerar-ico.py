#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Monta o instalador/logo.ico a partir do src/.../logo.png.

O .ico e exigido pelo jpackage para gravar o icone dentro do .exe. Isto aqui
so e usado quando o logo muda - nao faz parte do programa, que continua sem
dependencia nenhuma.

Uso:  python3 exemplo/gerar-ico.py
      (precisa do java, para redimensionar o png)
"""
import os
import struct
import subprocess
import sys
import tempfile

TAMANHOS = [16, 24, 32, 48, 64, 128, 256]

REDIMENSIONADOR = r'''
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class Redimensionar {
    public static void main(String[] a) throws Exception {
        BufferedImage origem = ImageIO.read(new File(a[0]));
        int lado = Integer.parseInt(a[2]);
        BufferedImage saida = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = saida.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(origem, 0, 0, lado, lado, null);
        g.dispose();
        ImageIO.write(saida, "png", new File(a[1]));
    }
}
'''


def main():
    raiz = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    origem = os.path.join(raiz, "src", "br", "com", "triangulo", "gerador", "logo.png")
    destino = os.path.join(raiz, "instalador", "logo.ico")
    if not os.path.isfile(origem):
        sys.exit("nao achei " + origem)

    with tempfile.TemporaryDirectory() as tmp:
        java = os.path.join(tmp, "Redimensionar.java")
        open(java, "w").write(REDIMENSIONADOR)
        subprocess.run(["javac", "-nowarn", "-d", tmp, java], check=True)

        imagens = []
        for lado in TAMANHOS:
            png = os.path.join(tmp, "logo-%d.png" % lado)
            subprocess.run(["java", "-cp", tmp, "Redimensionar", origem, png, str(lado)],
                           check=True)
            imagens.append((lado, open(png, "rb").read()))

        # ICONDIR + uma ICONDIRENTRY por tamanho, e o PNG inteiro como dado.
        # O Windows aceita PNG dentro de .ico desde o Vista.
        cabecalho = struct.pack("<HHH", 0, 1, len(imagens))
        deslocamento = len(cabecalho) + 16 * len(imagens)
        entradas, dados = b"", b""
        for lado, png in imagens:
            entradas += struct.pack("<BBBBHHII",
                                    0 if lado == 256 else lado,   # 0 quer dizer 256
                                    0 if lado == 256 else lado,
                                    0, 0, 1, 32, len(png), deslocamento)
            dados += png
            deslocamento += len(png)
        open(destino, "wb").write(cabecalho + entradas + dados)
        print("gravei %s com %d tamanhos: %s" % (destino, len(imagens),
                                                 ", ".join(str(t) for t in TAMANHOS)))


if __name__ == "__main__":
    main()
