#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Monta o exemplo/modelo-base.xlsx: a planilha em branco para o pessoal usar.

So tem a aba Base, com a linha 1 de cabecalho e as colunas ja formatadas -
data como data, valor como numero. Isso nao e enfeite: celula formatada como
data faz o Excel criar uma data de verdade quando a pessoa digita, e nao um
texto que o programa gravaria literal.

De proposito NAO tem linha de exemplo: exemplo esquecido vira registro falso
no arquivo. O exemplo mora no proprio cabecalho.

Uso:  python3 exemplo/gerar-modelo-base.py
"""
import os
import zipfile

REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
PRINCIPAL = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"

# coluna, titulo, largura, estilo da coluna toda
COLUNAS = [
    ("A", "DATA\n(ex.: 10/09/2025)", 16, 2),
    ("B", "D\ndebito (ex.: 895)", 14, 0),
    ("C", "C\ncredito (ex.: 520)", 14, 0),
    ("D", "VALOR\n(ex.: 734,99)", 16, 3),
    ("E", "COD. HIST.\n(ex.: 70)", 13, 0),
    ("F", "DESCRICAO\n(ex.: DELL PARC 06/12 *CARTAO DE CREDITO)", 62, 0),
]


def esc(t):
    return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
 <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
 <Default Extension="xml" ContentType="application/xml"/>
 <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
 <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
 <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
 <Relationship Id="rId1" Type="%s/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""" % REL

WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="%s" xmlns:r="%s">
 <sheets><sheet name="Base" sheetId="1" r:id="rId1"/></sheets>
</workbook>""" % (PRINCIPAL, REL)

WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
 <Relationship Id="rId1" Type="%(r)s/worksheet" Target="worksheets/sheet1.xml"/>
 <Relationship Id="rId2" Type="%(r)s/styles" Target="styles.xml"/>
</Relationships>""" % {"r": REL}

# xf0 geral | xf1 cabecalho | xf2 data | xf3 valor com duas casas
STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="%s">
 <numFmts count="1"><numFmt numFmtId="164" formatCode="dd/mm/yyyy"/></numFmts>
 <fonts count="2">
  <font><sz val="11"/><name val="Calibri"/></font>
  <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
 </fonts>
 <fills count="3">
  <fill><patternFill patternType="none"/></fill>
  <fill><patternFill patternType="gray125"/></fill>
  <fill><patternFill patternType="solid"><fgColor rgb="FF1D5B9A"/><bgColor indexed="64"/></patternFill></fill>
 </fills>
 <borders count="2">
  <border/>
  <border><left style="thin"><color rgb="FFBFBFBF"/></left><right style="thin"><color rgb="FFBFBFBF"/></right><top style="thin"><color rgb="FFBFBFBF"/></top><bottom style="thin"><color rgb="FFBFBFBF"/></bottom></border>
 </borders>
 <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
 <cellXfs count="4">
  <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
  <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
   <alignment horizontal="left" vertical="center" wrapText="1"/></xf>
  <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
  <xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
 </cellXfs>
</styleSheet>""" % PRINCIPAL


def main():
    raiz = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    destino = os.path.join(raiz, "exemplo", "modelo-base.xlsx")

    cols = "".join(
        '<col min="%d" max="%d" width="%d" customWidth="1"%s/>'
        % (i + 1, i + 1, largura, ' style="%d"' % estilo if estilo else "")
        for i, (_, _, largura, estilo) in enumerate(COLUNAS))

    celulas = "".join(
        '<c r="%s1" s="1" t="inlineStr"><is><t xml:space="preserve">%s</t></is></c>'
        % (letra, esc(titulo))
        for letra, titulo, _, _ in COLUNAS)

    sheet = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="%s">
 <sheetViews><sheetView tabSelected="1" workbookViewId="0">
  <pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>
  <selection pane="bottomLeft" activeCell="A2" sqref="A2"/>
 </sheetView></sheetViews>
 <sheetFormatPr defaultRowHeight="15"/>
 <cols>%s</cols>
 <sheetData><row r="1" ht="34" customHeight="1">%s</row></sheetData>
</worksheet>""" % (PRINCIPAL, cols, celulas)

    with zipfile.ZipFile(destino, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", CONTENT_TYPES)
        z.writestr("_rels/.rels", RELS)
        z.writestr("xl/workbook.xml", WORKBOOK)
        z.writestr("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
        z.writestr("xl/styles.xml", STYLES)
        z.writestr("xl/worksheets/sheet1.xml", sheet)
    print("gravei " + destino)


if __name__ == "__main__":
    main()
