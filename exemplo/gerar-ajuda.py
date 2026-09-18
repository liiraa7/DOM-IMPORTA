# -*- coding: utf-8 -*-
"""Gera os dois textos de ajuda da janela como literais Java ASCII."""
import html.entities

AZUL = "#1D5B9A"
ESCURO = "#102E54"
VERDE = "#00743E"
LARANJA = "#B56500"
VERMELHO = "#B01C1C"

CABECA = ("<html><body style='font-family:sans-serif; font-size:12px; margin:4px 10px 10px 10px'>")

COMO_FUNCIONA = CABECA + f"""
<h2 style='color:{ESCURO}; margin-bottom:2px'>Como usar</h2>
<div style='color:#5F6976'>O passo a passo, do comeco ao arquivo pronto.</div>
<hr>

<p>O programa lê a sua planilha e grava o arquivo de texto que o sistema importa.
Ele <b>nunca altera a planilha</b> &mdash; pode até deixá-la aberta no Excel.</p>

<h3 style='color:{AZUL}'>Antes de começar</h3>
<p>A planilha precisa ter a aba <b>Base</b> preenchida: uma linha por registro, das
colunas <b>A até I</b>, a partir da <b>linha 2</b>. A linha 1 é o cabeçalho.</p>

<h3 style='color:{AZUL}'>Passo 1 &mdash; apontar a planilha</h3>
<p>No campo <b>Planilha</b>, clique em <b>Selecionar...</b> e escolha o arquivo.
Se aparecer uma linha vermelha, é porque o arquivo não está lá ou não tem a aba
Base &mdash; leia o que ela diz.</p>
<p>Depois da primeira vez o caminho fica guardado: nos meses seguintes já vem
preenchido.</p>

<h3 style='color:{AZUL}'>Passo 2 &mdash; preencher os três campos</h3>
<table cellpadding='4' cellspacing='0'>
<tr><td><b>Empresa*</b></td><td>o código da empresa, por exemplo <b>744</b></td></tr>
<tr><td><b>Tipo*</b></td><td>o que está sendo importado, por exemplo <b>PARCELAMENTOS</b></td></tr>
<tr><td><b>Competência</b></td><td>o mês, por exemplo <b>082025</b></td></tr>
</table>
<p>Os dois com asterisco são obrigatórios. <b>É deles que sai o nome do arquivo</b>:</p>
<pre style='background:#F2F5F9; padding:6px; font-size:11px'>744 + PARCELAMENTOS + 082025  =  744_PARCELAMENTOS_082025.txt</pre>

<h3 style='color:{AZUL}'>Passo 3 &mdash; escolher a pasta</h3>
<p>No campo <b>Pasta</b>, clique em <b>Selecionar...</b> e escolha onde o arquivo
deve ser gravado. Também fica guardado para as próximas vezes.</p>

<h3 style='color:{AZUL}'>Passo 4 &mdash; conferir a linha azul</h3>
<p style='background:#EAF1F9; padding:6px'>A linha azul, logo abaixo dos campos,
mostra <b>o arquivo exato</b> que vai ser gravado, com pasta e nome completos.
<b>Leia essa linha antes de clicar.</b> É a sua última chance de notar um mês
errado ou uma pasta errada.</p>
<p>Se aparecer uma linha laranja avisando que esse arquivo <b>já foi gerado</b> em
tal data, pare e confira a competência: quase sempre é o mês que ficou do
mês passado.</p>

<h3 style='color:{AZUL}'>Passo 5 &mdash; gerar</h3>
<p>Clique em <b>Gerar arquivo</b> (ou aperte <b>Enter</b>). Leva menos de um
segundo.</p>

<h3 style='color:{AZUL}'>Passo 6 &mdash; ler o resultado</h3>
<p>O quadro <b>Registro</b> conta o que aconteceu, e a cor já diz o que é:</p>
<table cellpadding='5' cellspacing='0'>
<tr style='background:#E8F4EC'><td><b style='color:{VERDE}'>verde</b></td>
    <td>deu certo. Mostra quantos registros e quantos bytes</td></tr>
<tr style='background:#FFF6E5'><td><b style='color:{LARANJA}'>laranja</b></td>
    <td>gerou, mas tem algo para você conferir na planilha</td></tr>
<tr style='background:#FDEBEB'><td><b style='color:{VERMELHO}'>vermelho</b></td>
    <td>não gerou nada. A aba <b>Se der erro</b> explica o que fazer</td></tr>
</table>
<p><b>Aviso laranja não é erro</b>, mas também não é para ignorar: ele aponta a
célula exata que merece um olhar.</p>

<h3 style='color:{AZUL}'>Passo 7 &mdash; conferir o arquivo</h3>
<p><b>Abrir txt</b> abre o arquivo gerado; <b>Abrir pasta</b> abre a pasta dele.
Na primeira vez, vale abrir e dar uma olhada antes de importar no sistema.</p>

<h3 style='color:{AZUL}'>No mês seguinte</h3>
<p>Abra o programa: planilha, empresa, tipo e pasta já vêm preenchidos.
<b>Normalmente só a competência muda.</b> Troque o mês, confira a linha azul e
gere.</p>

<h3 style='color:{AZUL}'>Os botões</h3>
<table cellpadding='5' cellspacing='0'>
<tr><td><b>Gerar arquivo</b></td><td>lê a planilha e grava o txt &nbsp;(atalho: Enter)</td></tr>
<tr style='background:#FAFBFD'><td><b>Abrir txt</b></td><td>abre o arquivo que acabou de ser gerado</td></tr>
<tr><td><b>Abrir pasta</b></td><td>abre a pasta onde ele foi gravado</td></tr>
<tr style='background:#FAFBFD'><td><b>Recarregar config</b></td><td>lê de novo as configurações, se alguém as mudou por fora</td></tr>
</table>

<h3 style='color:{VERMELHO}'>Se aparecer erro</h3>
<p>Leia a mensagem em vermelho e veja a aba <b>Se der erro</b>: as causas comuns
estão lá, com o que fazer em cada uma.</p>
<p style='background:#FDEBEB; padding:6px'>Se não resolver, <b>fale com o desenvolvedor do
programa</b>. Leve junto o arquivo <b>gerador_arquivo.log</b>, que fica na
pasta do programa &mdash; o caminho está no rodapé desta janela. Esse arquivo guarda
o erro completo, com data e hora, e é o que resolve a dúvida mais rápido.</p>
</body></html>"""

SE_DER_ERRO = CABECA + f"""
<h2 style='color:{ESCURO}; margin-bottom:2px'>Se der erro</h2>
<div style='color:#5F6976'>O que costuma dar errado, o que fazer, e a quem
recorrer.</div>
<hr>

<p><b>Primeiro:</b> leia a linha vermelha no quadro <b>Registro</b>. Ela diz o que
aconteceu, em português. Quase sempre a resposta está na tabela abaixo.</p>

<h3 style='color:{VERMELHO}'>Não gerou nada</h3>
<table cellpadding='5' cellspacing='0'>
<tr style='background:#F2F5F9'><td><b>O que a mensagem diz</b></td><td><b>O que fazer</b></td></tr>
<tr><td><b>Não encontrei a planilha</b></td>
    <td>alguém moveu, renomeou, ou a rede caiu. Clique em <b>Selecionar...</b> e
    aponte o arquivo de novo</td></tr>
<tr style='background:#FAFBFD'><td><b>Não parece uma planilha do Excel</b></td>
    <td>é um arquivo <b>.xls</b> antigo. Abra no Excel e salve como
    <b>.xlsx</b> ou <b>.xlsm</b></td></tr>
<tr><td><b>A aba Base não existe</b></td>
    <td>a aba foi renomeada ou tem espaço sobrando no nome. A mensagem lista as
    abas que existem na planilha</td></tr>
<tr style='background:#FAFBFD'><td><b>Preencha Empresa</b> / <b>Preencha Tipo</b></td>
    <td>são obrigatórios: é deles que sai o nome do arquivo</td></tr>
<tr><td><b>Escolha a pasta</b></td><td>falta dizer onde gravar</td></tr>
<tr style='background:#FAFBFD'><td><b>A aba Base não tem nenhuma linha preenchida</b></td>
    <td>confira se os dados não foram colados em outra aba</td></tr>
<tr><td><b>Tem um caractere que não existe em windows-1252</b></td>
    <td>alguém colou um emoji ou um símbolo estranho de um site. A mensagem diz a
    linha; apague o caractere na planilha. Acento comum, &ccedil; e &atilde;
    podem ficar</td></tr>
<tr style='background:#FAFBFD'><td><b>O arquivo já existe. Você deve excluí-lo</b></td>
    <td>é de propósito, para não apagar sem querer um arquivo bom. Apague o txt
    antigo e gere de novo</td></tr>
<tr><td><b>Não consegui criar a pasta</b></td>
    <td>a unidade de rede está fora do ar, ou você não tem permissão nela</td></tr>
</table>

<h3 style='color:{LARANJA}'>Gerou, mas avisou em laranja</h3>
<p>O arquivo está gravado. O aviso aponta algo na planilha que merece conferência:</p>
<ul>
<li><b>Fórmula sem valor calculado</b> &mdash; abra a planilha no Excel, deixe
    calcular e salve. O campo saiu vazio no arquivo.</li>
<li><b>Barra vertical no meio do texto</b> &mdash; o <code>|</code> separa os campos
    do arquivo, então ele foi trocado por espaço. O aviso diz em qual célula.</li>
<li><b>Coluna A vazia com dados no resto da linha</b> &mdash; pode ser dado colado
    na linha errada. A linha foi gravada assim mesmo.</li>
<li><b>Este arquivo já foi gerado em tal data</b> &mdash; confira a competência
    antes de gravar por cima.</li>
<li><b>Tem dados fora do intervalo de colunas</b> &mdash; alguém escreveu à direita
    da última coluna que o programa lê, e <b>isso não entrou no arquivo</b>. Ou a
    informação está na coluna errada, ou o intervalo precisa ser aumentado; o
    aviso diz quais colunas e a partir de qual linha.</li>
</ul>

<h3 style='color:{VERDE}'>Quando o arquivo sai certo mas o conteúdo está errado</h3>
<p>Estas quatro coisas o programa <b>não tem como perceber</b>. Se o sistema
recusar a importação, ou os valores saírem estranhos, comece por aqui:</p>
<ul>
<li><b>O que vale é o valor, não o que aparece na tela.</b> Uma célula que mostra
    <b>1.234,50</b> pode ter o valor 1234,5 &mdash; e é 1234,5 que vai para o
    arquivo.</li>
<li><b>Data tem de ser data de verdade.</b> Se foi digitada como texto, sai como
    está escrita (<b>31.01.26</b> continua <b>31.01.26</b>). E data numa célula
    formatada como Geral sai como número (<b>46053</b>).</li>
<li><b>Célula mesclada</b> guarda o valor só na primeira célula; as outras vão
    vazias para o arquivo.</li>
<li><b>Linha escondida por filtro é lida do mesmo jeito.</b> O filtro esconde da
    sua vista, não do programa.</li>
</ul>

<h3 style='color:{AZUL}'>Nada disso resolveu</h3>
<p style='background:#FDEBEB; padding:6px'><b>Fale com o desenvolvedor do programa</b>.<br><br>
Leve junto o arquivo <b>gerador_arquivo.log</b>, da pasta do programa &mdash; o
caminho completo está no rodapé desta janela. Ele guarda todo erro com data e
hora, e poupa muito tempo de adivinhação.<br><br>
Se puder, diga também: o que você estava gerando, qual planilha, e o que a linha
vermelha dizia.</p>
</body></html>"""


def para_java(nome, texto):
    """Devolve as linhas Java do literal, com todo caractere fora do ASCII
    virando entidade HTML - assim o fonte continua 100% ASCII."""
    saida = []
    for ch in texto:
        if ord(ch) < 128:
            saida.append(ch)
        else:
            nome_ent = html.entities.codepoint2name.get(ord(ch))
            saida.append("&%s;" % nome_ent if nome_ent else "&#%d;" % ord(ch))
    limpo = "".join(saida)
    # uma linha Java por linha do HTML, sem passar de ~100 colunas
    linhas = []
    for linha in limpo.split("\n"):
        linha = linha.replace("\\", "\\\\").replace('"', '\\"')
        # o HTML colapsa espaco, mas colar duas linhas sem nenhum junta palavras
        if linha and not linha.endswith(" "):
            linha = linha + " "
        while len(linha) > 86:
            corte = linha.rfind(" ", 0, 86)
            if corte <= 0:
                corte = 86
            linhas.append(linha[:corte + 1])
            linha = linha[corte + 1:]
        linhas.append(linha)
    corpo = "\n".join('                + "%s"' % l for l in linhas if l != "")
    return "        private static String %s() {\n            return \"\"\n%s;\n        }" % (nome, corpo)


if __name__ == "__main__":
    import sys
    with open(sys.argv[1], "w") as f:
        f.write(para_java("textoComoFunciona", COMO_FUNCIONA))
        f.write("\n\n")
        f.write(para_java("textoSeDerErro", SE_DER_ERRO))
        f.write("\n")
    print("gerado")
