#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Monta a planilha-exemplo.xlsx usada no rito de teste.

Isto NAO faz parte do programa: e so a ferramenta que recria o arquivo de
teste. O Gerador em si continua sem nenhuma dependencia (java.util.zip + StAX).

A planilha e escrita a mao, em XML, de proposito: ela cobre os dois jeitos de
o texto aparecer num .xlsx - sharedStrings (t="s", como o Excel grava) e
inlineStr (como o openpyxl grava) - e ainda uma formula com valor calculado,
uma linha vazia no meio e duas celulas com formato de data.

Uso:  python3 gerar-planilha-exemplo.py [saida.xlsx] [pastaB9] [nomeB10]
"""
import datetime
import sys
import zipfile

REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"


def serial(iso):
    """Data -> numero de serie do Excel (sistema 1900)."""
    return (datetime.date.fromisoformat(iso) - datetime.date(1899, 12, 30)).days


def esc(texto):
    return (texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
 <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
 <Default Extension="xml" ContentType="application/xml"/>
 <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
 <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
 <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
 <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
 <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
 <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
 <Relationship Id="rId1" Type="%s/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""" % REL

WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="%s">
 <sheets>
  <sheet name="Padrao" sheetId="1" r:id="rId1"/>
  <sheet name="Principal" sheetId="2" r:id="rId2"/>
  <sheet name="Base" sheetId="3" r:id="rId3"/>
 </sheets>
</workbook>""" % REL

WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
 <Relationship Id="rId1" Type="%(r)s/worksheet" Target="worksheets/sheet1.xml"/>
 <Relationship Id="rId2" Type="%(r)s/worksheet" Target="worksheets/sheet2.xml"/>
 <Relationship Id="rId3" Type="%(r)s/worksheet" Target="worksheets/sheet3.xml"/>
 <Relationship Id="rId4" Type="%(r)s/sharedStrings" Target="sharedStrings.xml"/>
 <Relationship Id="rId5" Type="%(r)s/styles" Target="styles.xml"/>
</Relationships>""" % {"r": REL}

# xf 0 = geral; xf 1 = data (numFmtId 14); xf 2 = mascara #.##0,00 (numFmtId 164)
STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
 <numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.00"/></numFmts>
 <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
 <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
 <borders count="1"><border/></borders>
 <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
 <cellXfs count="3">
  <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
  <xf numFmtId="14" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
  <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
 </cellXfs>
</styleSheet>"""

VAZIA = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData/></worksheet>"""


def main():
    saida = sys.argv[1] if len(sys.argv) > 1 else "planilha-exemplo.xlsx"
    pasta_b9 = sys.argv[2] if len(sys.argv) > 2 else "C:/Users/Lira/Desktop"
    nome_b10 = sys.argv[3] if len(sys.argv) > 3 else "744_PARCELAMENTOS_082025"

    # textos que o Excel guardaria no sharedStrings (linha 2 da Base e a Principal)
    textos = [
        "Codigo do cliente", "744",
        "Competencia", "082025",
        "Pasta de destino", pasta_b9,
        "Nome do arquivo", nome_b10,
        "001", "JO\u00c3O ATACAD\u00c3O LTDA", "acordo especial", "FIM",
    ]
    idx = {t: i for i, t in enumerate(textos)}
    shared = ["<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>",
              "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"%d\" uniqueCount=\"%d\">" % (len(textos), len(textos))]
    for t in textos:
        shared.append("<si><t xml:space=\"preserve\">%s</t></si>" % esc(t))
    shared.append("</sst>")

    def s(ref, texto):
        return '<c r="%s" t="s"><v>%d</v></c>' % (ref, idx[texto])

    def inline(ref, texto):
        return '<c r="%s" t="inlineStr"><is><t xml:space="preserve">%s</t></is></c>' % (ref, esc(texto))

    def num(ref, valor, estilo=0):
        return '<c r="%s"%s><v>%s</v></c>' % (ref, ' s="%d"' % estilo if estilo else "", valor)

    principal = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
<row r="6">%s%s</row>
<row r="7">%s%s</row>
<row r="9">%s%s</row>
<row r="10">%s%s</row>
</sheetData></worksheet>""" % (
        s("A6", "Codigo do cliente"), s("B6", "744"),
        s("A7", "Competencia"), s("B7", "082025"),
        s("A9", "Pasta de destino"), s("B9", pasta_b9),
        s("A10", "Nome do arquivo"), s("B10", nome_b10))

    # Linha 2: texto pelo sharedStrings, valor 1234,5 com mascara #.##0,00
    #          (a mascara mostra 1.234,50; o programa tem de gravar 1234,5).
    # Linha 3: vazia de proposito - o buraco no meio nao pode cortar o arquivo.
    # Linha 4: texto inline (openpyxl) e uma formula com valor ja calculado.
    base = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
<row r="1">%s</row>
<row r="2">%s%s%s%s%s%s%s</row>
<row r="3"/>
<row r="4">%s%s%s%s%s%s%s%s%s</row>
</sheetData></worksheet>""" % (
        "".join(inline("%s1" % c, n) for c, n in zip("ABCDEFGHI", [
            "Codigo", "Nome", "Valor", "Vencimento", "Parcelas", "Observacao", "X", "Y", "Z"])),
        s("A2", "001"), s("B2", "JO\u00c3O ATACAD\u00c3O LTDA"), num("C2", "1234.5", 2),
        num("D2", serial("2026-01-31"), 1), num("E2", "3"),
        s("F2", "acordo especial"), s("I2", "FIM"),
        inline("A4", "002"), inline("B4", "MARIA & CIA"), num("C4", "1000"),
        num("D4", serial("2026-02-01"), 1), num("E4", "10"),
        '<c r="F4" t="str"><f>IF(E4&gt;0,"ok","")</f><v>ok</v></c>',
        inline("G4", "A"), inline("H4", "B"), inline("I4", "C"))

    with zipfile.ZipFile(saida, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", CONTENT_TYPES)
        z.writestr("_rels/.rels", RELS)
        z.writestr("xl/workbook.xml", WORKBOOK)
        z.writestr("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
        z.writestr("xl/styles.xml", STYLES)
        z.writestr("xl/sharedStrings.xml", "".join(shared))
        z.writestr("xl/worksheets/sheet1.xml", VAZIA)
        z.writestr("xl/worksheets/sheet2.xml", principal)
        z.writestr("xl/worksheets/sheet3.xml", base)
    print("gravei " + saida)


if __name__ == "__main__":
    main()
