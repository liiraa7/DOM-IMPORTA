# Adapted Dom Import

Antigo "Gerador de Arquivo TXT". Programa Java que lê a planilha do escritório e
grava o txt no layout **6000/6100**. Substitui as macros VBA `lDom`, `ISel` e `Verifica_Arquivo`, que
a empresa vive desativando. A planilha fica limpa: o programa **só lê** o
arquivo, nunca escreve nele.

- Classe única, sem nenhuma biblioteca externa (o leitor de `.xlsx`/`.xlsm` é
  `java.util.zip` + StAX do próprio JDK).
- Roda em **Java 8** ou mais novo.
- Tudo que é layout está no `config.properties` — mudar coluna, prefixo,
  separador ou codificação não exige recompilar.

## Usar

| Quero | Faço |
| --- | --- |
| Abrir a janela | `rodar.bat` (ou `java -jar gerador-arquivo-txt.jar`) |
| Gerar sem janela (agendador) | `gerar-agora.bat` — no Agendador de Tarefas, com o argumento `--agendador` e `saida.sobrescrever=sempre` |
| Testar com outro config | `java -jar gerador-arquivo-txt.jar --console --config exemplo\config-exemplo.properties` |
| Recompilar depois de mexer no fonte | `compilar.bat` (precisa do JDK) |
| Conferir a saída contra o gabarito | `exemplo\conferir.bat` |
| Descobrir por que não abriu | `diagnostico.bat` |
| Empacotar para instalar em outra máquina | `criar-instalador.bat` |

O `config.properties` e o `gerador_arquivo.log` ficam na mesma pasta do jar — é
o caminho que aparece no rodapé da janela.

Se o `rodar.bat` não abrir nada, rode o `diagnostico.bat`: ele diz em que pasta
está, se achou o Java, se o jar e a planilha existem e ainda gera o txt de
teste. A saída dele é o que se manda para quem for ajudar.

## O que sai

```
(linha em branco)
6000|X||||
6100|001|JOÃO ATACADÃO LTDA|1234,5|31/01/2026|3|acordo especial|||FIM|
6000|X||||
6100|002|MARIA & CIA|1000|01/02/2026|10|ok|A|B|C|
```

A planilha só precisa da aba **Base**. A antiga aba `Principal` existia para
montar o nome do arquivo e dizer onde salvar — hoje isso é do programa, e uma aba
a menos é uma aba a menos para alguém digitar por engano.

Um par `6000`/`6100` por linha preenchida da aba **Base**, gravado em
**windows-1252** com quebra **CRLF**, começando com uma linha em branco — do
mesmo jeito que o `Print #` do VBA fazia.

## Instalar na máquina de outra pessoa

Na **sua** máquina, uma vez:

```
criar-instalador.bat
```

Ele monta a pasta `dist\` com o programa empacotado e o Java embutido. Compacte
essa pasta num zip.

Na **outra** máquina: descompactar o zip e dar dois cliques em `instalar.bat`.
Pronto — atalho na área de trabalho e no menu Iniciar.

- Não precisa de senha de administrador.
- **Não precisa ter Java instalado**: ele vai dentro do programa.
- Reinstalar por cima preserva o `config.properties` com os campos já
  preenchidos.
- Para remover: `desinstalar.bat`.

Se a máquina tiver o WiX Toolset v3, o `criar-instalador.bat` gera também um
`.msi` de verdade, para instalar com duplo clique sem o `.bat`.

## Configuração

Abra o `config.properties` no Bloco de Notas. Os pontos que mais se mexe:

| Chave | Para que serve |
| --- | --- |
| `planilha.caminho` | onde está a planilha (barra normal: `C:/pasta/arquivo.xlsm`) |
| `planilha.abaBase` | nome da aba com os dados |
| `saida.empresa` / `saida.tipo` / `saida.competencia` | o que monta o nome do arquivo |
| `saida.pasta` | onde gravar |
| `saida.nomePadrao` | molde do nome: `{empresa}_{tipo}_{competencia}` |
| `controle.usarAbaPrincipal` | `true` volta a ler B6/B7/B9/B10 da aba Principal |
| `base.colunaInicial` / `base.colunaFinal` / `base.linhaInicial` | intervalo lido da Base |
| `base.pararNaLinhaVazia` | `true` volta ao jeito da macro: para na primeira linha vazia |
| `layout.*` | prefixos, separador e os campos do 6000 |
| `saida.codificacao` / `saida.quebraLinha` | como o txt é gravado |
| `saida.sobrescrever` | `recusar` (igual à macro), `perguntar` ou `sempre` |

> Barra invertida é caractere de escape em `.properties`: escreva o caminho com
> barra normal (`C:/pasta`) ou dupla (`C:\\pasta`). Barra simples engole o
> caractere seguinte em silêncio.

## Na janela

Três abas:

- **Gerar arquivo** — a tela de trabalho. Você preenche **Empresa**, **Tipo** e
  **Competência**, escolhe a **Pasta**, e a linha azul mostra em tempo real o
  arquivo exato que vai ser gravado: `744_PARCELAMENTOS_082025.txt`. O campo
  **Abas** mostra os nomes que existem de verdade na planilha. O **Registro**
  colore cada linha: aviso em laranja, erro em vermelho, resultado em verde.
- **Como funciona** — o manual, dentro do programa: o que ele faz, o que espera em
  B6, B7, B9 e B10, o que espera na aba Base, o que sai no txt e como usar.
- **Se der erro** — o que na planilha faz o programa parar e como resolver, o que
  só gera aviso, e as quatro coisas que saem erradas **sem** aviso nenhum.

Atalhos: `Enter` gera o arquivo, `Alt+T` abre o txt gerado, `Alt+P` abre a pasta,
`Alt+R` recarrega o config.

## O que o programa avisa em vez de esconder

- Célula com fórmula sem valor calculado (a planilha precisa ser aberta e salva).
- `|` no meio de um dado: vira espaço e aparece um aviso na tela.
- Caractere que não existe em windows-1252: a geração para e diz qual é, em vez
  de gravar `?` no lugar.
- Aba encontrada com outra caixa (`PRINCIPAL` x `Principal`): usa a que existe e
  avisa.
- Linha com dados mas coluna A vazia: grava e avisa.

## Pastas

```
src/br/com/triangulo/gerador/GeradorArquivo.java   o programa inteiro
src/br/com/triangulo/gerador/logo.png              o logo do escritorio
config.properties                                  layout e caminhos
compilar.bat  rodar.bat  gerar-agora.bat           atalhos do dia a dia
exemplo/                                           planilha de teste + gabarito
CLAUDE.md                                          briefing e regras do projeto
```

Antes de mexer no código, leia o `CLAUDE.md`: ele tem as regras da casa (Java 8,
classe única, zero dependência) e as armadilhas que já custaram caro.

---

Feito por **Ronald Lira** — Triangulo Contabilidade.
