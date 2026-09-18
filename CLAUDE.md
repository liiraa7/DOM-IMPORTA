# ADAPTED DOM IMPORT — briefing do projeto

## O que é
Programa Java standalone que substitui as macros VBA `lDom`, `Verifica_Arquivo`
e `ISel` de uma planilha do escritório. A empresa remove/desativa macros com
frequência, obrigando a refazer o processo na mão. Agora a planilha fica limpa:
o programa apenas LÊ o arquivo e escreve o txt no layout 6000/6100.

Versão atual: **3.11.0**. Autor: **Ronald Lira** (Triangulo Contabilidade). Classe única `GeradorArquivo.java`
(pacote `br.com.triangulo.gerador`), **sem dependência externa**.

Histórico: começou em Python (1.0.1, Tkinter), virou Java em 15/09/2026 porque o
dono programa melhor em Java. Em 17/09/2026 o Apache POI e o Maven foram
removidos — o leitor de .xlsx passou a ser escrito à mão com zip + StAX do JDK.
Em 17/09/2026 o projeto entrou no git (repositório `liiraa7/DOM-IMPORTA`) e o
nome da aba passou a ser procurado sem diferenciar maiúsculas de minúsculas.
Os pull requests #1 e #2 juntaram tudo na `main` e a branch de trabalho foi
apagada: **o trabalho acontece direto na `main`**, sem branch nem PR no meio.
A 3.3.0 reformou a janela: campos agrupados, avisos em laranja e erros em
vermelho no registro, barra de estado com o resumo da geração e o crédito do
autor. A 3.4.0 trouxe a faixa azul com o "by Ronald Lira" e três abas —
**Gerar arquivo**, **Como funciona** e **Se der erro** — as duas últimas com o
manual do programa dentro da própria janela (o botão "Sobre" saiu: a aba
"Como funciona" faz o serviço).

**A 3.5.0 tirou a aba Principal do caminho.** Ela só servia para montar o nome
do arquivo e dizer onde salvar, e isso virou trabalho do programa: os campos
**Empresa**, **Tipo** e **Competência** ficam na janela, o nome sai do molde
`saida.nomePadrao` (`{empresa}_{tipo}_{competencia}`) e a pasta é escolhida na
tela. A planilha agora precisa **só da aba Base** — decisão do Ronald, com a
razão certa: aba que existe é aba onde alguém vai digitar por engano. O jeito
antigo continua disponível em `controle.usarAbaPrincipal=true`, e um
`saida.destino` de config velho é migrado sozinho (a pasta, e os três campos
quando o nome tem a cara `EMPRESA_TIPO_COMPETENCIA`).

A 3.6.0 trocou a xícara do Java pelo logo do escritório: ele é o ícone da janela
(barra de título, barra de tarefas, Alt+Tab) e aparece na faixa azul, ao lado do
título. A 3.7.0 batizou o programa de **ADAPTED DOM IMPORT** (constante
`NOME_PROGRAMA`) e tirou da tela o que era conversa interna: o subtítulo do
layout 6000/6100 e das macros, o nome da chave `saida.nomePadrao` na dica, e o
caminho do config no Registro (continua no rodapé e no log). A lista de abas
virou aviso: só aparece, em vermelho, quando a planilha não abre ou não tem a
aba dos dados — e aí mostra as que existem, que é quando isso serve.

Os nomes de arquivo **não** mudaram: o jar continua `gerador-arquivo-txt.jar` e
os .bat com os mesmos nomes, para não quebrar atalho nem tarefa agendada de
quem já instalou.

A **3.8.0** trouxe duas coisas: a guarda de competência repetida e o
empacotamento para instalar na máquina dos outros.

- **Guarda de competência**: o config guarda `saida.ultimoArquivo` e
  `saida.ultimaGeracao`. Se o nome montado for igual ao da última geração, a
  janela avisa em laranja (*"este mesmo arquivo já foi gerado em ..."*) e o
  Gerar pergunta antes. É o engano mais provável numa rotina mensal: esquecer
  de trocar a competência e gravar o mês novo por cima do anterior. No
  `--console` não há quem responda, então vira aviso e segue — o agendador não
  pode travar. Desistir na pergunta levanta `Cancelado`, que a janela trata em
  cinza, sem caixa de erro: desistir não é falha.
- **`config.properties` na pasta do usuário**: se a pasta do programa não
  aceitar escrita (instalado em Program Files), `configDoUsuario()` leva o
  config para `%APPDATA%\Adapted Dom Import`, copiando o que veio instalado.
  O teste de escrita é escrever de verdade, porque `canWrite()` mente em
  algumas pastas do Windows.

A **3.9.0** tirou o `config.properties` do repositório. Ele é reescrito pelo
programa a cada geração e é diferente em cada máquina — ficava causando
conflito em todo `git pull`, e um colega que instalasse herdava a pasta e a
competência de quem empacotou. Agora:

- o repositório guarda o **`config-modelo.properties`** (molde, com os campos
  em branco);
- o `config.properties` está no `.gitignore`;
- na primeira abertura o programa copia o modelo (`configDoUsuario()`), e os
  `.bat` fazem o mesmo antes de rodar;
- o `criar-instalador.bat` empacota o **modelo**, nunca o config de quem montou.

A **3.10.0** virou as abas de ajuda para o usuário, não para quem programa. A
"Como funciona" — que explicava layout, config e história das macros — deu lugar
a **"Como usar"**, um passo a passo de sete passos, do apontar a planilha até
conferir o arquivo. A "Se der erro" passou a ser tabela de *mensagem na tela →
o que fazer*. As duas terminam mandando **falar com o desenvolvedor do programa** e levar o
`gerador_arquivo.log` — sem citar o nome: crédito de autoria é na faixa e no
rodapé, socorro é função, não pessoa.

A constante `CONTATO` carrega essa frase e aparece em **todo** erro: na caixa de
diálogo, no Registro e no `--console`. Caixa de diálogo com texto comprido passa
por `caixaDeTexto()`, que põe largura fixa em HTML — sem isso a caixa estica numa
linha só e passa da tela.

A **3.11.0** fechou um buraco que a pergunta do Ronald abriu: *"e se a linha
passar do I?"*. Passava — e o dado sumia calado. Agora `Aba` guarda também a
`ultimaColuna` com dado, e `lerRegistros()` avisa quando existe conteúdo à
direita de `base.colunaFinal`, dizendo quais colunas, quantas linhas e a partir
de qual. Um aviso só para a planilha inteira, não um por linha.

## A planilha de verdade (print de 17/09/2026)
A aba Principal da planilha do escritório era assim — e é dela que vieram os
campos que hoje estão na janela:

| Célula | Campo | Exemplo |
| --- | --- | --- |
| B5 | Competência | `082025` |
| B6 | Empresa* | `744` |
| B7 | Tipo* | `PARCELAMENTOS` |
| B9 | Pasta | `I:\999 - IMPORTA\744\` |
| B10 | Nome do Arquivo | `744_PARCELAMENTOS_082025` |

O asterisco era dos "Campos Obrigatórios" — os mesmos `controle.obrigatorias=B6,B7`
do config. O nome era `Empresa_Tipo_Competência` e a pasta terminava no número da
empresa. Hoje isso é montado pelo programa.

## O que a macro fazia (fonte original guardado no chat)
- `lDom` — varria a aba "Base" da linha 2 até a primeira linha com a coluna A
  vazia, escrevendo na aba "Padrao" o par `6000|X||||` + `6100|A|B|...|I|`.
- `ISel` — concatenava a coluna A da aba "Padrao", começando com um `vbCrLf`, e
  gravava com `Open ... For Append` + `Print #`.
- `Verifica_Arquivo` — se o txt já existia, mostrava "você deve excluir para
  gravar" e não fazia nada; se não existia, perguntava antes de criar.
- Controle na aba "Principal": B6/B7 obrigatórios, B9 pasta, B10 nome sem `.txt`.

A aba "Padrao" não existe mais no fluxo: as linhas são montadas na memória.

## Decisões já tomadas — não reabrir sem motivo
- Planilha fixa, caminho no `config.properties`. Sem tela de seleção.
- Classe única com Swing. Classes aninhadas, não espalhar em arquivos.
- Nenhuma biblioteca externa. Isso é regra, não preferência: o dono ficou preso
  na instalação do Maven e o programa tem de rodar com um `javac` só.
- Tudo que é layout mora no `config.properties`: colunas, linha inicial,
  prefixos, separador, células de controle, codificação.
- Empacotamento: `jpackage --type app-image` (pasta com .exe, não arquivo único).

## Armadilhas que já custaram caro (não regredir)
1. **Codificação**: windows-1252 com CRLF, como o `Print #`. Encoder com
   `CodingErrorAction.REPORT` — por padrão o Java troca caractere fora da tabela
   por `?` em silêncio.
2. **Valor da célula**: usar o valor bruto, não a máscara. Máscara `#.##0,00`
   exibindo 1.234,50 tem `.Value` = 1234,5 e era isso que o VBA gravava.
   (Foi por isso que o Apache POI + `DataFormatter` teria divergido da macro.)
3. **Parar na primeira linha vazia**: um buraco no meio da Base cortava o
   arquivo pela metade. Hoje varre até o fim e pula a vazia
   (`base.pararNaLinhaVazia` traz o jeito antigo de volta).
4. **Fórmula sem valor calculado**: no .xlsx vem sem `<v>` ou com `<v>` vazio.
   Fórmula que devolve texto vazio de propósito tem `t="str"` e NÃO deve entrar
   no aviso — foi o falso positivo achado no teste de 17/09.
5. **`|` dentro do dado** vira espaço + aviso na tela. Nunca escondido.
6. **Escape no `.properties`**: barra invertida é escape; caminho com barra
   normal ou dupla. Por isso o programa regrava `saida.destino` sempre com
   barra normal.
7. **Textos do .xlsx**: o Excel grava em `sharedStrings.xml` (`t="s"`), o
   openpyxl grava `inlineStr`. O leitor tem de aguentar os dois — os dois estão
   cobertos e testados.
8. **Erro do SwingWorker aparece embrulhado**: `get()` levanta
   `ExecutionException` e o nome da classe vazava para a tela
   (`java.lang.IllegalStateException: Preencha Tipo`). O `mensagem()`
   desembrulha — não tirar.
9. **Nome de aba com outra caixa**: a planilha real tem "PRINCIPAL" e o config
   pedia "Principal" — a geração morria com `IllegalStateException`. Hoje o
   programa procura o nome exato, e só depois tenta ignorando maiúsculas,
   minúsculas e espaços, avisando na tela qual aba usou. Não voltar a comparar
   com `equals` puro.

## REGRAS DE OURO PARA QUEM MEXER NESTE CÓDIGO

1. **A máquina do Rogerio tem apenas Java 8 de EXECUÇÃO** (1.8.0_461, sem JDK
   por padrão). Compile SEMPRE com `javac --release 8`. O `--release` recusa a
   compilação se qualquer API de Java 9+ entrar no código — é a rede de
   segurança, não confie em conferência manual.
   Proibidos, portanto: records, switch expression, text block, `var`,
   `String.isBlank`, `Files.writeString/readString`, `List.of`, `Path.of`.
   Já existe o helper `vazio(String)` no lugar do `isBlank`.
2. **Nenhuma dependência externa.** Sem Maven, sem Gradle, sem Apache POI.
   O leitor de .xlsx é escrito à mão com `java.util.zip` + StAX. Isso é regra,
   não preferência: ele travou instalando Maven e o programa tem de compilar
   com um `javac` só.
3. **Classe única.** Não quebrar em vários arquivos.
4. **O programa NUNCA escreve na planilha.** Só lê. (Escreve o txt e, no
   `config.properties`, as linhas `saida.empresa`, `saida.tipo`,
   `saida.competencia`, `saida.pasta`, `saida.ultimoArquivo` e
   `saida.ultimaGeracao` — mais nada.)
5. **Toda entrega termina com o `.jar` recompilado**, não só o `.java` — sem
   JDK na ponta, o fonte sozinho não serve para nada.

## Ciclo de trabalho (Claude Code)

```
javac --release 8 -encoding UTF-8 -nowarn -d out src\br\com\triangulo\gerador\GeradorArquivo.java
java -cp out br.com.triangulo.gerador.GeradorArquivo --console
jar --create --file gerador-arquivo-txt.jar --main-class br.com.triangulo.gerador.GeradorArquivo -C out .
```

No VS Code as três linhas acima estão prontas como tarefas: `Ctrl+Shift+B`
roda o rito inteiro, e `Executar Tarefa` lista as outras (ver `.vscode/tasks.json`).
O editor **não** garante o alvo Java 8 — o Language Server usa o JDK instalado e
aceitaria `var` ou `record` sem reclamar. Quem recusa é o `--release 8` do
`compilar.bat`, ou seja, a tarefa 1: ela é obrigatória antes de entregar.

O `--console` roda sem janela e usa a planilha e o destino do
`config.properties` — é assim que se testa uma alteração sem clicar em nada.
Depois confira os bytes do txt: tem de começar com `0d 0a`, ter `0d 0a` em toda
quebra e `c3` no Ã de "JOÃO".

Para testar sem mexer no `config.properties` de produção existe o
`--config <arquivo>`:

```
java -jar gerador-arquivo-txt.jar --console --config exemplo\config-exemplo.properties
```

## Empacotamento (instalar na máquina dos outros)

`criar-instalador.bat` roda **no Windows, com JDK 21**, e produz `dist\`:

1. chama o `compilar.bat` (compila, confere o gabarito, monta o jar);
2. `jpackage --type app-image` com `--icon instalador\logo.ico` → pasta
   `dist\Adapted Dom Import\` com o `.exe` e **o Java embutido**;
3. tenta também `--type msi` (precisa do WiX Toolset v3; sem ele, avisa e segue);
4. copia para `dist\` o `instalar.bat`, o `desinstalar.bat` e o `LEIA-ME.txt`.

Na máquina de destino: descompactar o zip e rodar `instalar.bat`. Ele copia para
`%LOCALAPPDATA%\Adapted Dom Import`, cria atalho na área de trabalho e no menu
Iniciar (via VBScript, usando `SpecialFolders`, que respeita Desktop redirecionado
para o OneDrive) e **preserva o `config.properties` de uma instalação anterior**.
Não pede senha de administrador e **não exige Java na máquina**.

Detalhes que custaram teste:
- `--add-modules java.base,java.desktop,java.logging,java.xml` derruba o Java
  embutido de 157 MB para 83 MB. O `jdeps` não lista `java.xml` porque ele entra
  junto com `java.desktop` — está declarado assim mesmo, de propósito.
- A versão sai do próprio `GeradorArquivo.java` por `findstr`, para não existir
  um segundo lugar para esquecer de atualizar.
- O `.ico` do `.exe` é gerado por `exemplo/gerar-ico.py` a partir do
  `logo.png` (7 tamanhos, PNG dentro do ICO). Só precisa rodar quando o logo
  mudar.

## Rito antes de empacotar
1. `javac --release 8 -Xlint:all,-options -Werror -encoding UTF-8 -d out src\...\GeradorArquivo.java`
   (tem de passar sem um warning sequer; o `-options` só cala o aviso do JDK
   novo sobre o alvo 8 ser antigo)
2. `exemplo\conferir.bat` — gera o txt da `exemplo\planilha-exemplo.xlsx` e
   compara byte a byte com o `exemplo\gabarito.txt` (`fc /b`).
3. `compilar.bat` faz os dois passos acima e monta o jar.

## Saída esperada (gabarito, com a planilha-exemplo.xlsx)
```
(linha em branco)
6000|X||||
6100|001|JOÃO ATACADÃO LTDA|1234,5|31/01/2026|3|acordo especial|||FIM|
6000|X||||
6100|002|MARIA & CIA|1000|01/02/2026|10|ok|A|B|C|
```
Bytes: CRLF (`0d 0a`), acentos em windows-1252 (`c3` para Ã).
O arquivo está gravado em `exemplo\gabarito.txt` — é o que o `fc /b` compara.

A `planilha-exemplo.xlsx` foi feita de propósito para cobrir as armadilhas:
texto em `sharedStrings` na linha 2 e `inlineStr` na linha 4, máscara
`#.##0,00` sobre o 1234,5, duas datas com formato de data, uma linha vazia no
meio e uma fórmula com o valor já calculado. Para recriá-la:
`python3 exemplo\gerar-planilha-exemplo.py` (a ferramenta é só do teste; o
programa continua sem dependência nenhuma).

## Onde mexer
- Layout, colunas, codificação, células de controle → `config.properties` (o seu,
  local). Mudança que todos devem receber vai no **`config-modelo.properties`**,
  que é o que está versionado e o que o instalador leva.
- Ordem ou separador do nome do arquivo → `saida.nomePadrao` no config. As peças
  são `{empresa}`, `{tipo}` e `{competencia}`; peça vazia não deixa separador
  solto (`744__082025` sai `744_082025`).
- Campos da janela e a prévia do arquivo → `Janela.montarEntrada()` e
  `Janela.atualizarPrevia()`. Cuidado com o `DocumentListener`: ele dispara
  durante o preenchimento da tela, e sem o guarda `preenchendo` o
  `copiarCampos()` apaga o que veio do config — foi bug de verdade em 17/09.
- Nome do autor e da empresa na janela → constantes `AUTOR` e `EMPRESA`.
- Frase de socorro em erro → constante `CONTATO`, no alto da classe. Ela usa
  `NOME_LOG`, então tem de vir declarada **depois** dele: antes dá
  *illegal forward reference*. Quem quiser pôr ramal, e-mail ou WhatsApp para os
  colegas, é essa linha.
- Texto das abas de ajuda → `textoComoFunciona()` (a aba "Como usar") e
  `textoSeDerErro()`, no fim da
  classe `Janela`. É HTML, e **o fonte não tem um único caractere fora do ASCII**:
  acento entra como entidade (`&ccedil;`, `&atilde;`). Isso é de propósito — assim
  o arquivo compila igual em qualquer máquina, com ou sem `-encoding UTF-8`. Para
  reescrever o texto com acento de verdade e converter, existe o
  `exemplo/gerar-ajuda.py`.
- Logo → `src/br/com/triangulo/gerador/logo.png`, 256x256 com fundo
  transparente. É **recurso, não código**: a regra da classe única continua
  valendo. Ele é carregado de dentro do jar por `logo()` / `logoEm()` / `icones()`.
  **O `compilar.bat` copia o png para `out/` antes de montar o jar** — sem essa
  cópia o programa roda, mas volta para o ícone do Java. Quem compilar na mão
  precisa copiar também. Trocar o logo é substituir o arquivo, nada mais.
- Cores da tela → constantes no alto da classe `Janela` (`AZUL`, `VERDE`,
  `LARANJA`, `VERMELHO` e as versões `_FUNDO`).
- O registro colorido é um `JTextPane`. Ele só quebra a linha entre palavras, e
  caminho de arquivo não tem espaço: por isso existem `KitQueQuebra`,
  `FabricaQueQuebra` e `RotuloQueQuebra` — sem eles o caminho passa da borda e
  desaparece. Não trocar por `JTextArea` (perde a cor) nem tirar o kit (volta a
  esconder o fim do caminho).
- Regra nova de formatação (zeros à esquerda, campo de tamanho fixo) →
  `GeradorArquivo.formatarNumero()`, aí recompila.
- Data com outro formato → `LeitorPlanilha.formatarData()`.

## A planilha modelo para o pessoal
`exemplo/modelo-base.xlsx`, feita por `exemplo/gerar-modelo-base.py`: só a aba
`Base`, cabeçalho congelado e colunas formatadas (A como data, D com duas casas).
A formatação é o ponto: célula de data faz o Excel criar data de verdade em vez
de texto, que é a armadilha que o programa não consegue avisar. Sem linha de
exemplo de propósito — exemplo esquecido vira registro falso no txt; o exemplo
está no texto do cabeçalho.

As colunas seguem o print da Base real de 18/09/2026:
`A DATA | B débito | C crédito | D VALOR | E cód. histórico | F DESCRIÇÃO`.

## Pendências
1. Testar com a planilha real e comparar o txt linha a linha com o da macro.
2. **Quantos campos o sistema espera?** A Base real tem 6 colunas (A–F), mas o
   config vem com `base.colunaFinal=I`, o que acrescenta 3 campos vazios no fim
   de cada linha. Confirmar contra um txt da macro.
3. **Casas decimais.** `R$ 2.959,50` sai como `2959,5` — valor bruto, como o VBA
   gravava. Se o sistema exigir sempre duas casas, criar uma regra por coluna no
   config (`saida.casasDecimais=D:2`, por exemplo) em `formatarNumero()`.
4. Confirmar se o sistema de destino aceita a linha em branco inicial; se não,
   `saida.linhaEmBrancoNoInicio=false`.
5. Conferir se a aba da planilha real se chama "PRINCIPAL" mesmo — só importa
   para quem usar `controle.usarAbaPrincipal=true`.
6. Depois de conferir o txt contra o da macro, **apagar a aba Principal da
   planilha de verdade**: o programa não precisa mais dela, e aba que existe é
   aba onde alguém digita por engano.
