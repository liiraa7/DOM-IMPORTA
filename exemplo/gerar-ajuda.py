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
<h2 style='color:{ESCURO}; margin-bottom:2px'>ADAPTED DOM IMPORT</h2>
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

<h3 style='color:{AZUL}'>2. A planilha só precisa da aba Base</h3>
<p>Nada de aba <b>Principal</b>, nada de célula de controle. Aquela aba só servia para
montar o nome do arquivo e dizer onde salvar — e isso agora é trabalho <b>deste
programa</b>, nos campos da aba <b>Gerar arquivo</b>.</p>
<p>É melhor assim por um motivo simples: aba que existe na planilha é aba onde alguém
vai acabar digitando por engano.</p>
<p>Quem ainda tiver a planilha antiga e quiser o jeito de antes põe
<code>controle.usarAbaPrincipal=true</code> no config, e aí voltam a valer B6 e B7
obrigatórias, B9 para a pasta e B10 para o nome.</p>

<h3 style='color:{AZUL}'>3. De onde sai o nome do arquivo</h3>
<p>Dos três campos da tela, nesta ordem:</p>
<pre style='background:#F2F5F9; padding:6px; font-size:11px'>Empresa  Tipo            Competência
744    _ PARCELAMENTOS _ 082025      &nbsp;=&nbsp; 744_PARCELAMENTOS_082025.txt</pre>
<p><b>Empresa</b> e <b>Tipo</b> são obrigatórios — é o mesmo asterisco que a planilha
antiga tinha. Competência pode ficar em branco, e aí o nome sai sem ela, sem
deixar separador solto.</p>
<p>A <b>Pasta</b> é escolhida no botão <b>Selecionar...</b>. A linha azul logo abaixo dos
campos mostra, em tempo real, <b>o arquivo exato</b> que o Gerar vai escrever — leia
essa linha antes de clicar.</p>
<p>A ordem do nome mora no config, em <code>saida.nomePadrao</code>. O molde de fábrica é
<code>{{empresa}}_{{tipo}}_{{competencia}}</code>; trocar a ordem ou o separador é
editar essa linha, sem recompilar nada.</p>

<h3 style='color:{AZUL}'>4. O que ele espera na aba Base</h3>
<p>Uma linha por registro, das colunas <b>A até I</b>, começando na <b>linha 2</b> — a
linha 1 é o cabeçalho e é ignorada.</p>
<p>Linha totalmente vazia no meio da Base é <b>pulada</b>, e a varredura continua até o
fim. A macro antiga parava na primeira vazia e cortava o arquivo pela metade;
quem quiser o jeito antigo põe <code>base.pararNaLinhaVazia=true</code> no config.</p>

<h3 style='color:{AZUL}'>5. O que sai no arquivo</h3>
<p>Para cada linha da Base, duas linhas no txt:</p>
<pre style='background:#F2F5F9; padding:6px; font-size:11px'>6000|X||||
6100|001|JO&Atilde;O ATACAD&Atilde;O LTDA|1234,5|31/01/2026|3|acordo|||FIM|</pre>
<p>O arquivo começa com uma <b>linha em branco</b>, é gravado em <b>windows-1252</b> e
quebra linha com <b>CRLF</b> — exatamente como o <code>Print #</code> do VBA fazia. Mudar
qualquer uma dessas três coisas é mexer no config, não no programa.</p>

<h3 style='color:{AZUL}'>6. Como usar no dia a dia</h3>
<ol>
<li>Confira o caminho da <b>Planilha</b>. O campo <b>Abas</b> mostra os nomes que existem
    de verdade no arquivo — serve de conferência.</li>
<li>Preencha <b>Empresa</b>, <b>Tipo</b> e <b>Competência</b>, e escolha a <b>Pasta</b>.</li>
<li>Leia a linha azul: é o arquivo que vai ser gravado.</li>
<li>Clique <b>Gerar arquivo</b> (ou aperte Enter).</li>
<li>Leia o <b>Registro</b>. Laranja é aviso, vermelho é erro, verde é o resultado.</li>
<li><b>Abrir txt</b> abre o arquivo gerado; <b>Abrir pasta</b> abre a pasta dele.</li>
</ol>
<p>Os campos ficam guardados: na próxima abertura vêm preenchidos como você deixou.
Em geral só a <b>Competência</b> muda de um mês para o outro.</p>

<h3 style='color:{AZUL}'>7. Onde ficam as configurações</h3>
<p>Na mesma pasta do programa ficam o <b>config.properties</b> — caminhos, colunas,
prefixos, codificação, molde do nome — e o <b>gerador_arquivo.log</b>, que guarda
toda geração e todo erro, com data e hora. O caminho exato está no pé desta
janela.</p>
<p>Depois de editar o config, clique <b>Recarregar config</b>: não precisa fechar o
programa.</p>

<h3 style='color:{AZUL}'>8. Sem janela, para o agendador</h3>
<p>O <code>gerar-agora.bat</code> gera o txt sem abrir nada, usando os campos guardados no
config. É o que se coloca no Agendador de Tarefas do Windows. Nesse caso deixe
<code>saida.sobrescrever=sempre</code>, senão a segunda execução recusa gravar porque o
arquivo do dia anterior ainda está lá.</p>
</body></html>"""

SE_DER_ERRO = CABECA + f"""
<h2 style='color:{ESCURO}; margin-bottom:2px'>Se der erro</h2>
<div style='color:#5F6976'>O que faz o programa parar, e o que só muda o resultado sem
avisar alto.</div>
<hr>

<h3 style='color:{VERMELHO}'>Faz o programa PARAR sem gerar nada</h3>
<table cellpadding='5' cellspacing='0'>
<tr style='background:#F2F5F9'><td><b>O que está errado</b></td><td><b>O que fazer</b></td></tr>
<tr><td><b>Planilha não está no caminho</b> do config — alguém moveu, renomeou
    ou a rede caiu</td><td>clique <b>Selecionar...</b> e aponte o arquivo</td></tr>
<tr style='background:#FAFBFD'><td><b>Arquivo é .xls antigo</b> (formato binário) ou
    está corrompido</td><td>abra no Excel e salve como <b>.xlsx</b> ou <b>.xlsm</b></td></tr>
<tr><td><b>Aba Base não existe</b> com esse nome — renomeada, com espaço sobrando,
    ou escrita diferente</td><td>o erro lista as abas encontradas; ajuste
    <code>planilha.abaBase</code> no config. Maiúscula/minúscula o programa resolve
    sozinho e avisa</td></tr>
<tr style='background:#FAFBFD'><td><b>Empresa ou Tipo em branco</b></td>
    <td>preencha os dois: é deles que sai o nome do arquivo</td></tr>
<tr><td><b>Pasta em branco</b></td><td>escolha a pasta no
    <b>Selecionar...</b></td></tr>
<tr style='background:#FAFBFD'><td><b>Aba Base sem nenhuma linha preenchida</b> a partir
    da linha 2</td><td>confira se os dados não foram colados em outra aba</td></tr>
<tr><td><b>Caractere que não existe em windows-1252</b> — emoji, símbolo grego,
    caractere colado de site</td><td>o erro diz a linha; apague o caractere na planilha.
    Acento comum, &ccedil;, ~ e &deg; podem ficar: esses existem na tabela</td></tr>
<tr style='background:#FAFBFD'><td><b>O txt já existe</b> e o config está em
    <code>recusar</code></td><td>apague o txt antigo — é de propósito, era o que a macro
    <b>Verifica_Arquivo</b> fazia. Para sobrescrever, mude para <code>perguntar</code>
    ou <code>sempre</code></td></tr>
<tr><td><b>Pasta não existe e não pode ser criada</b> — unidade de rede fora do ar,
    sem permissão</td><td>confira se o I: ou a pasta da rede está acessível</td></tr>
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
<li><b>Nome da aba com outra caixa</b> (BASE x Base). Funciona, mas o aviso fica
    aparecendo até o config bater com o nome de verdade.</li>
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
