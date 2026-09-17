# Gerador de Arquivo TXT

Programa Java que lê a planilha do escritório e grava o txt no layout
**6000/6100**. Substitui as macros VBA `lDom`, `ISel` e `Verifica_Arquivo`, que
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

Um par `6000`/`6100` por linha preenchida da aba **Base**, gravado em
**windows-1252** com quebra **CRLF**, começando com uma linha em branco — do
mesmo jeito que o `Print #` do VBA fazia.

## Configuração

Abra o `config.properties` no Bloco de Notas. Os pontos que mais se mexe:

| Chave | Para que serve |
| --- | --- |
| `planilha.caminho` | onde está a planilha (barra normal: `C:/pasta/arquivo.xlsm`) |
| `planilha.abaPrincipal` / `planilha.abaBase` | nomes das abas |
| `base.colunaInicial` / `base.colunaFinal` / `base.linhaInicial` | intervalo lido da Base |
| `base.pararNaLinhaVazia` | `true` volta ao jeito da macro: para na primeira linha vazia |
| `layout.*` | prefixos, separador e os campos do 6000 |
| `saida.codificacao` / `saida.quebraLinha` | como o txt é gravado |
| `saida.sobrescrever` | `recusar` (igual à macro), `perguntar` ou `sempre` |
| `saida.destino` | caminho do txt; em branco usa a pasta (B9) e o nome (B10) da aba Principal |

> Barra invertida é caractere de escape em `.properties`: escreva o caminho com
> barra normal (`C:/pasta`) ou dupla (`C:\\pasta`). Barra simples engole o
> caractere seguinte em silêncio.

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
config.properties                                  layout e caminhos
compilar.bat  rodar.bat  gerar-agora.bat           atalhos do dia a dia
exemplo/                                           planilha de teste + gabarito
CLAUDE.md                                          briefing e regras do projeto
```

Antes de mexer no código, leia o `CLAUDE.md`: ele tem as regras da casa (Java 8,
classe única, zero dependência) e as armadilhas que já custaram caro.
