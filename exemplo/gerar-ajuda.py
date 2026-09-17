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
<h2 style='color:{ESCURO}; margin-bottom:2px'>Gerador de Arquivo TXT</h2>
<div style='color:#5F6976'>O que este programa faz, e o que ele espera encontrar na planilha.</div>
<hr>

<h3 style='color:{AZUL}'>1. Para que ele serve</h3>
<p>Ele faz o que as macros <b>lDom</b>, <b>ISel</b> e <b>Verifica_Arquivo</b> faziam:
lê a planilha e grava um arquivo de texto no layout <b>6000/6100</b>, pronto para
ser importado no sistema.</p>
<p>A diferença é que a planilha agora pode ficar <b>limpa, sem macro nenhuma</b> — e
assim a empresa pode desativar macros à vontade, que o trabalho continua saindo.</p>
<p style='background:#E8F4EC; padding:6px'><b>O programa nunca escreve na planilha.</b>
Ele só lê. Pode rodar com a planilha aberta que nada nela muda.</p>

<h3 style='color:{AZUL}'>2. O que ele espera na aba Principal</h3>
<table cellpadding='4' cellspacing='0'>
<tr><td><b>B6</b></td><td>obrigatória — se estiver vazia, o programa recusa gerar</td></tr>
<tr><td><b>B7</b></td><td>obrigatória — mesma coisa</td></tr>
<tr><td><b>B9</b></td><td>pasta onde salvar o txt</td></tr>
<tr><td><b>B10</b></td><td>nome do arquivo, <b>sem</b> o <code>.txt</code></td></tr>
</table>
<p>B9 e B10 só são usadas quando o campo <b>Salvar txt em</b> está em branco. Se você
escolher o destino na tela, ele manda.</p>

<h3 style='color:{AZUL}'>3. O que ele espera na aba Base</h3>
<p>Uma linha por registro, das colunas <b>A até I</b>, começando na <b>linha 2</b> — a
linha 1 é o cabeçalho e é ignorada. Cada linha preenchida virá a ser um par de
linhas no txt.</p>
<p>Linha totalmente vazia no meio da Base é <b>pulada</b>, e a varredura continua até o
fim. A macro antiga parava na primeira vazia e cortava o arquivo pela metade;
quem quiser o jeito antigo põe <code>base.pararNaLinhaVazia=true</code> no config.</p>

<h3 style='color:{AZUL}'>4. O que sai no arquivo</h3>
<p>Para cada linha da Base, duas linhas no txt:</p>
<pre style='background:#F2F5F9; padding:6px; font-size:11px'>6000|X||||
6100|001|JO&Atilde;O ATACAD&Atilde;O LTDA|1234,5|31/01/2026|3|acordo|||FIM|</pre>
<p>O arquivo começa com uma <b>linha em branco</b>, é gravado em <b>windows-1252</b> e
quebra linha com <b>CRLF</b> — exatamente como o <code>Print #</code> do VBA fazia. Mudar
qualquer uma dessas três coisas é mexer no config, não no programa.</p>

<h3 style='color:{AZUL}'>5. Como usar no dia a dia</h3>
<ol>
<li>Confira o caminho da <b>Planilha</b>. O campo <b>Abas</b> mostra os nomes que existem
    de verdade no arquivo — serve de conferência.</li>
<li>Deixe <b>Salvar txt em</b> em branco para usar B9 e B10, ou escolha o destino.</li>
<li>Clique <b>Gerar arquivo</b> (ou aperte Enter).</li>
<li>Leia o <b>Registro</b>. Laranja é aviso, vermelho é erro, verde é o resultado.</li>
<li><b>Abrir txt</b> abre o arquivo gerado; <b>Abrir pasta</b> abre a pasta dele.</li>
</ol>

<h3 style='color:{AZUL}'>6. Onde ficam as configurações</h3>
<p>Na mesma pasta do programa ficam o <b>config.properties</b> — caminhos, colunas,
prefixos, codificação — e o <b>gerador_arquivo.log</b>, que guarda toda geração e
todo erro, com data e hora. O caminho exato está no pé desta janela.</p>
<p>Depois de editar o config, clique <b>Recarregar config</b>: não precisa fechar o
programa.</p>

<h3 style='color:{AZUL}'>7. Sem janela, para o agendador</h3>
<p>O <code>gerar-agora.bat</code> gera o txt sem abrir nada, usando o config. É o que se
coloca no Agendador de Tarefas do Windows. Nesse caso deixe
<code>saida.sobrescrever=sempre</code>, senão a segunda execução recusa gravar porque o
arquivo do dia anterior ainda está lá.</p>
</body></html>"""

SE_DER_ERRO = CABECA + f"""
<h2 style='color:{ESCURO}; margin-bottom:2px'>Se der erro</h2>
<div style='color:#5F6976'>O que na planilha faz o programa parar, e o que só muda o
resultado sem avisar alto.</div>
<hr>

<h3 style='color:{VERMELHO}'>Faz o programa PARAR sem gerar nada</h3>
<table cellpadding='5' cellspacing='0'>
<tr style='background:#F2F5F9'><td><b>O que está errado</b></td><td><b>O que fazer</b></td></tr>
<tr><td><b>Planilha não está no caminho</b> do config — alguém moveu, renomeou
    ou a rede caiu</td><td>clique <b>Selecionar...</b> e aponte o arquivo, ou corrija
    <code>planilha.caminho</code></td></tr>
<tr style='background:#FAFBFD'><td><b>Arquivo é .xls antigo</b> (formato binário) ou
    está corrompido</td><td>abra no Excel e salve como <b>.xlsx</b> ou <b>.xlsm</b></td></tr>
<tr><td><b>Aba Principal ou Base não existe</b> com esse nome — renomeada, com
    espaço sobrando, ou escrita diferente</td><td>o erro lista as abas encontradas;
    ajuste o nome no config. Maiúscula/minúscula o programa resolve sozinho e avisa</td></tr>
<tr style='background:#FAFBFD'><td><b>B6 ou B7 vazias</b> na aba Principal</td>
    <td>preencha as duas; são as células de controle que a macro também exigia</td></tr>
<tr><td><b>Destino em branco na tela E B9/B10 vazias</b> na planilha</td>
    <td>preencha B9 e B10, ou escolha o destino no campo <b>Salvar txt em</b></td></tr>
<tr style='background:#FAFBFD'><td><b>Aba Base sem nenhuma linha preenchida</b> a partir
    da linha 2</td><td>confira se os dados não foram colados em outra aba</td></tr>
<tr><td><b>Caractere que não existe em windows-1252</b> — emoji, símbolo grego,
    caractere colado de site</td><td>o erro diz a linha; apague o caractere na planilha.
    Acento comum, &ccedil;, ~ e &deg; podem ficar: esses existem na tabela</td></tr>
<tr style='background:#FAFBFD'><td><b>O txt já existe</b> e o config está em
    <code>recusar</code></td><td>apague o txt antigo — é de propósito, era o que a macro
    <b>Verifica_Arquivo</b> fazia. Para sobrescrever, mude para <code>perguntar</code>
    ou <code>sempre</code></td></tr>
<tr><td><b>Pasta de destino não existe e não pode ser criada</b> — unidade de rede
    fora do ar, sem permissão</td><td>confira se o I: ou a pasta da rede está
    acessível</td></tr>
</table>

<h3 style='color:{LARANJA}'>Não para, mas muda o arquivo — sempre com aviso</h3>
<ul>
<li><b>Fórmula sem valor calculado.</b> O programa lê o valor que está gravado na
    planilha, não recalcula nada. Planilha salva por outro programa pode vir sem
    esse valor: abra no Excel, deixe calcular e salve. O campo sai vazio e o aviso
    aparece.</li>
<li><b>Barra vertical dentro do dado.</b> O <code>|</code> separa os campos, então um
    <code>|</code> digitado no meio do nome quebraria o layout. Ele é trocado por
    espaço e o aviso diz em qual célula.</li>
<li><b>Coluna A vazia com dados no resto da linha.</b> A linha é gravada e o aviso
    pede conferência — pode ser dado colado na linha errada.</li>
<li><b>Nome da aba com outra caixa</b> (PRINCIPAL x Principal). Funciona, mas o aviso
    fica aparecendo até o config bater com o nome de verdade.</li>
</ul>

<h3 style='color:{VERDE}'>Não avisa nada, e é onde mora o perigo</h3>
<p>Estas quatro coisas geram um arquivo <i>perfeito</i> — com o conteúdo errado. Vale
conferir na primeira vez:</p>
<ul>
<li><b>Máscara não vai para o txt, o valor vai.</b> Célula que mostra
    <b>1.234,50</b> tem valor 1234,5 e é <b>1234,5</b> que sai. Era o que o VBA
    gravava. Se o sistema exige duas casas sempre, isso tem de ser tratado.</li>
<li><b>Data tem de ser data de verdade.</b> Se a data foi digitada como texto, sai
    exatamente como está escrita — <b>31.01.26</b> continua <b>31.01.26</b>. E se a
    célula tem data mas está formatada como Geral, sai o número de série do Excel
    (<b>46053</b>) em vez da data.</li>
<li><b>Célula mesclada</b> guarda o valor só na primeira célula; as outras vêm
    vazias, e é isso que vai para o arquivo.</li>
<li><b>Linha oculta ou escondida por filtro é lida igual.</b> O filtro é enfeite de
    tela: para o programa, a linha está lá.</li>
</ul>
<p style='background:#FFF6E5; padding:6px'><b>Espaço sobrando no fim do texto também
vai para o arquivo</b>, porque o programa grava o que está na célula, sem aparar.</p>

<h3 style='color:{AZUL}'>Quando nada disso explicar</h3>
<p>O <b>gerador_arquivo.log</b>, na pasta do programa, guarda o erro completo com data e
hora. É o arquivo que resolve a dúvida — mande ele junto ao pedir ajuda.</p>
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
