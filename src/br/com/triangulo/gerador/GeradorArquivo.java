package br.com.triangulo.gerador;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.security.CodeSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Gerador de Arquivo TXT - layout 6000/6100.
 *
 * Substitui as macros VBA lDom, ISel e Verifica_Arquivo: le a planilha
 * (.xlsx/.xlsm) e escreve o txt. NUNCA escreve na planilha.
 *
 * Regras da casa (ver CLAUDE.md): classe unica, sem dependencia externa,
 * compilavel com "javac --release 8".
 */
public final class GeradorArquivo {

    static final String VERSAO = "3.2.3";
    static final String NOME_CONFIG = "config.properties";
    static final String NOME_LOG = "gerador_arquivo.log";

    private static final Logger LOG = Logger.getLogger(GeradorArquivo.class.getName());

    private GeradorArquivo() {
    }

    // ------------------------------------------------------------------
    // entrada
    // ------------------------------------------------------------------

    public static void main(String[] args) {
        boolean console = false;
        File arquivoConfig = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--console".equalsIgnoreCase(arg)) {
                console = true;
            } else if (arg.startsWith("--config=")) {
                arquivoConfig = new File(arg.substring("--config=".length()));
            } else if ("--config".equalsIgnoreCase(arg) && i + 1 < args.length) {
                i++;
                arquivoConfig = new File(args[i]);
            } else if ("--ajuda".equalsIgnoreCase(arg) || "--help".equalsIgnoreCase(arg)) {
                imprimirAjuda();
                return;
            }
        }
        if (arquivoConfig == null) {
            arquivoConfig = new File(pastaBase(), NOME_CONFIG);
        }
        prepararLog(arquivoConfig.getAbsoluteFile().getParentFile());
        if (console) {
            System.exit(executarConsole(arquivoConfig));
        } else {
            abrirJanela(arquivoConfig);
        }
    }

    private static void imprimirAjuda() {
        System.out.println("Gerador de Arquivo TXT v" + VERSAO);
        System.out.println("  (sem argumento)     abre a janela");
        System.out.println("  --console           gera o txt sem janela, usando o config.properties");
        System.out.println("  --config <arquivo>  usa outro config.properties");
    }

    private static int executarConsole(File arquivoConfig) {
        try {
            Config cfg = Config.carregar(arquivoConfig);
            System.out.println("Gerador de Arquivo TXT v" + VERSAO + " (console)");
            System.out.println("config   = " + cfg.arquivo.getAbsolutePath());
            System.out.println("planilha = " + cfg.planilhaCaminho);
            Resultado r = gerar(cfg, cfg.saidaDestino, new Confirmacao() {
                public boolean sobrescrever(File arquivo) {
                    java.io.Console console = System.console();
                    if (console == null) {
                        System.out.println("O arquivo ja existe e nao ha teclado para perguntar."
                                + " Use saida.sobrescrever=sempre no agendador.");
                        return false;
                    }
                    String resposta = console.readLine("O arquivo %s ja existe. Sobrescrever? (s/N) ",
                            arquivo.getAbsolutePath());
                    return resposta != null && resposta.trim().toLowerCase().startsWith("s");
                }
            });
            for (int i = 0; i < r.avisos.size(); i++) {
                System.out.println("AVISO: " + r.avisos.get(i));
            }
            System.out.println("registros = " + r.registros);
            System.out.println("bytes     = " + r.bytes);
            System.out.println("arquivo   = " + r.arquivo.getAbsolutePath());
            return 0;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "falha ao gerar", e);
            System.err.println("ERRO: " + mensagem(e));
            return 1;
        }
    }

    // ------------------------------------------------------------------
    // pastas e log
    // ------------------------------------------------------------------

    /** Pasta do jar/.exe - e onde moram o config.properties e o log. */
    static File pastaBase() {
        try {
            CodeSource cs = GeradorArquivo.class.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null) {
                URI uri = cs.getLocation().toURI();
                if ("file".equalsIgnoreCase(uri.getScheme())) {
                    File local = new File(uri);
                    File pasta = local.isDirectory() ? local : local.getParentFile();
                    if (pasta != null && pasta.isDirectory()) {
                        return pasta;
                    }
                }
            }
        } catch (URISyntaxException e) {
            LOG.log(Level.FINE, "nao consegui descobrir a pasta do programa", e);
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "nao consegui descobrir a pasta do programa", e);
        }
        return new File(System.getProperty("user.dir", "."));
    }

    private static void prepararLog(File pasta) {
        try {
            File destino = new File(pasta, NOME_LOG);
            FileHandler handler = new FileHandler(destino.getAbsolutePath(), true);
            handler.setFormatter(new SimpleFormatter());
            LOG.addHandler(handler);
            LOG.setUseParentHandlers(false);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "nao consegui abrir o arquivo de log", e);
        }
    }

    // ------------------------------------------------------------------
    // configuracao
    // ------------------------------------------------------------------

    static final class Config {

        final File arquivo;
        String planilhaCaminho;
        String abaPrincipal;
        String abaBase;
        String celulaPasta;
        String celulaNome;
        List<String> celulasObrigatorias;
        String colunaInicial;
        String colunaFinal;
        int linhaInicial;
        boolean pararNaLinhaVazia;
        String separador;
        String prefixoCabecalho;
        String camposCabecalho;
        String prefixoDetalhe;
        String substitutoSeparador;
        String codificacao;
        String quebraLinha;
        String extensao;
        String sobrescrever;
        String saidaDestino;
        boolean linhaEmBrancoNoInicio;

        private Config(File arquivo) {
            this.arquivo = arquivo;
        }

        static Config carregar(File arquivo) throws IOException {
            Config cfg = new Config(arquivo.getAbsoluteFile());
            Properties p = new Properties();
            if (cfg.arquivo.isFile()) {
                p.load(new StringReader(lerTexto(cfg.arquivo)));
            }
            cfg.planilhaCaminho = texto(p, "planilha.caminho", "");
            cfg.abaPrincipal = texto(p, "planilha.abaPrincipal", "Principal");
            cfg.abaBase = texto(p, "planilha.abaBase", "Base");
            cfg.celulaPasta = texto(p, "controle.celulaPasta", "B9");
            cfg.celulaNome = texto(p, "controle.celulaNome", "B10");
            cfg.celulasObrigatorias = lista(texto(p, "controle.obrigatorias", "B6,B7"));
            cfg.colunaInicial = texto(p, "base.colunaInicial", "A").toUpperCase();
            cfg.colunaFinal = texto(p, "base.colunaFinal", "I").toUpperCase();
            cfg.linhaInicial = inteiro(p, "base.linhaInicial", 2);
            cfg.pararNaLinhaVazia = logico(p, "base.pararNaLinhaVazia", false);
            cfg.separador = texto(p, "layout.separador", "|");
            cfg.prefixoCabecalho = texto(p, "layout.prefixoCabecalho", "6000");
            cfg.camposCabecalho = texto(p, "layout.camposCabecalho", "X,,,");
            cfg.prefixoDetalhe = texto(p, "layout.prefixoDetalhe", "6100");
            cfg.substitutoSeparador = p.getProperty("layout.substitutoSeparador", " ");
            cfg.codificacao = texto(p, "saida.codificacao", "windows-1252");
            cfg.quebraLinha = texto(p, "saida.quebraLinha", "crlf").toLowerCase();
            cfg.extensao = texto(p, "saida.extensao", ".txt");
            cfg.sobrescrever = texto(p, "saida.sobrescrever", "recusar").toLowerCase();
            cfg.saidaDestino = texto(p, "saida.destino", "");
            cfg.linhaEmBrancoNoInicio = logico(p, "saida.linhaEmBrancoNoInicio", true);
            cfg.validar();
            return cfg;
        }

        private void validar() {
            if (vazio(separador)) {
                throw new IllegalStateException("layout.separador nao pode ficar em branco.");
            }
            if (linhaInicial < 1) {
                throw new IllegalStateException("base.linhaInicial tem de ser 1 ou maior.");
            }
            if (colunaParaIndice(colunaFinal) < colunaParaIndice(colunaInicial)) {
                throw new IllegalStateException(
                        "base.colunaFinal (" + colunaFinal + ") vem antes de base.colunaInicial (" + colunaInicial + ").");
            }
            try {
                Charset.forName(codificacao);
            } catch (UnsupportedCharsetException e) {
                throw new IllegalStateException("saida.codificacao desconhecida: " + codificacao, e);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("saida.codificacao invalida: " + codificacao, e);
            }
            if (!"recusar".equals(sobrescrever) && !"perguntar".equals(sobrescrever)
                    && !"sempre".equals(sobrescrever)) {
                throw new IllegalStateException(
                        "saida.sobrescrever aceita recusar, perguntar ou sempre - veio \"" + sobrescrever + "\".");
            }
        }

        String quebra() {
            return "lf".equals(quebraLinha) ? "\n" : "\r\n";
        }

        /**
         * Regrava a linha saida.destino preservando comentarios e o resto do
         * arquivo. Caminho sempre com barra normal: no .properties a barra
         * invertida e escape e engoliria o caractere seguinte.
         */
        void gravarDestino(String caminho) {
            if (!arquivo.isFile()) {
                return;
            }
            try {
                String valor = caminho == null ? "" : caminho.replace('\\', '/');
                String texto = lerTexto(arquivo);
                String fim = texto.indexOf("\r\n") >= 0 ? "\r\n" : "\n";
                String[] linhas = texto.split("\\r?\\n", -1);
                boolean achou = false;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < linhas.length; i++) {
                    String linha = linhas[i];
                    if (linha.trim().startsWith("saida.destino=")) {
                        linha = "saida.destino=" + valor;
                        achou = true;
                    }
                    if (i > 0) {
                        sb.append(fim);
                    }
                    sb.append(linha);
                }
                if (!achou) {
                    sb.append(fim).append("saida.destino=").append(valor);
                }
                Files.write(arquivo.toPath(), sb.toString().getBytes("ISO-8859-1"));
                saidaDestino = caminho == null ? "" : caminho;
            } catch (IOException e) {
                LOG.log(Level.WARNING, "nao consegui regravar saida.destino no config.properties", e);
            }
        }

        private static String texto(Properties p, String chave, String padrao) {
            String v = p.getProperty(chave);
            return v == null ? padrao : v.trim();
        }

        private static int inteiro(Properties p, String chave, int padrao) {
            String v = texto(p, chave, "");
            if (vazio(v)) {
                return padrao;
            }
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new IllegalStateException(chave + " tem de ser um numero - veio \"" + v + "\".", e);
            }
        }

        private static boolean logico(Properties p, String chave, boolean padrao) {
            String v = texto(p, chave, "");
            return vazio(v) ? padrao : "true".equalsIgnoreCase(v) || "sim".equalsIgnoreCase(v);
        }

        private static List<String> lista(String valor) {
            List<String> saida = new ArrayList<String>();
            String[] partes = valor.split(",");
            for (int i = 0; i < partes.length; i++) {
                String parte = partes[i].trim();
                if (!vazio(parte)) {
                    saida.add(parte.toUpperCase());
                }
            }
            return saida;
        }
    }

    /**
     * Le o .properties como UTF-8 quando ele for UTF-8 valido e como
     * ISO-8859-1 (o padrao do Properties) quando nao for. Sem isso um caminho
     * com acento vira lixo dependendo de como o Bloco de Notas salvou.
     */
    static String lerTexto(File arquivo) throws IOException {
        byte[] bytes = Files.readAllBytes(arquivo.toPath());
        CharsetDecoder d = Charset.forName("UTF-8").newDecoder();
        d.onMalformedInput(CodingErrorAction.REPORT);
        d.onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return d.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            LOG.log(Level.FINE, "config nao e UTF-8, lendo como ISO-8859-1", e);
            return new String(bytes, "ISO-8859-1");
        }
    }

    // ------------------------------------------------------------------
    // leitura do .xlsx / .xlsm (zip + StAX, sem biblioteca externa)
    // ------------------------------------------------------------------

    /** Uma aba ja lida: valores por referencia ("B6") e a ultima linha vista. */
    static final class Aba {
        final String nome;
        final Map<String, String> celulas = new HashMap<String, String>();
        int ultimaLinha;

        Aba(String nome) {
            this.nome = nome;
        }

        String valor(String referencia) {
            String v = celulas.get(referencia.toUpperCase());
            return v == null ? "" : v;
        }
    }

    static final class LeitorPlanilha {

        private static final Set<Integer> FORMATOS_DATA = formatosData();

        private final ZipFile zip;
        private final List<String> avisos;
        private final LinkedHashMap<String, String> abas = new LinkedHashMap<String, String>();
        private final List<String> textos = new ArrayList<String>();
        private final List<Integer> formatoPorEstilo = new ArrayList<Integer>();
        private final Map<Integer, String> formatosPersonalizados = new HashMap<Integer, String>();
        private boolean base1904;

        LeitorPlanilha(File arquivo, List<String> avisos) throws IOException {
            if (!arquivo.isFile()) {
                throw new IllegalStateException("Nao encontrei a planilha: " + arquivo.getAbsolutePath());
            }
            this.avisos = avisos;
            try {
                this.zip = new ZipFile(arquivo);
            } catch (IOException e) {
                throw new IOException("Nao consegui abrir a planilha \"" + arquivo.getName()
                        + "\". Ela esta aberta no Excel ou nao e um .xlsx/.xlsm?", e);
            }
            try {
                lerWorkbook();
                lerTextosCompartilhados();
                lerEstilos();
            } catch (IOException e) {
                fechar();
                throw e;
            } catch (RuntimeException e) {
                fechar();
                throw e;
            }
        }

        List<String> nomesDasAbas() {
            return new ArrayList<String>(abas.keySet());
        }

        void fechar() {
            try {
                zip.close();
            } catch (IOException e) {
                LOG.log(Level.FINE, "falha ao fechar a planilha", e);
            }
        }

        // -------- partes do pacote

        private InputStream parte(String caminho) throws IOException {
            ZipEntry entrada = zip.getEntry(caminho);
            if (entrada == null) {
                return null;
            }
            return zip.getInputStream(entrada);
        }

        private void lerWorkbook() throws IOException {
            Map<String, String> alvoPorId = new HashMap<String, String>();
            InputStream rels = parte("xl/_rels/workbook.xml.rels");
            if (rels != null) {
                XMLStreamReader r = abrir(rels);
                try {
                    while (r.hasNext()) {
                        if (r.next() == XMLStreamConstants.START_ELEMENT
                                && "Relationship".equals(r.getLocalName())) {
                            String id = r.getAttributeValue(null, "Id");
                            String alvo = r.getAttributeValue(null, "Target");
                            if (id != null && alvo != null) {
                                alvoPorId.put(id, normalizarAlvo(alvo));
                            }
                        }
                    }
                } catch (XMLStreamException e) {
                    throw new IOException("xl/_rels/workbook.xml.rels ilegivel.", e);
                } finally {
                    fechar(r, rels);
                }
            }

            InputStream wb = parte("xl/workbook.xml");
            if (wb == null) {
                throw new IllegalStateException(
                        "Este arquivo nao parece uma planilha do Excel (nao achei xl/workbook.xml). "
                                + "Arquivo .xls antigo precisa ser salvo como .xlsx ou .xlsm.");
            }
            XMLStreamReader r = abrir(wb);
            try {
                int semRel = 0;
                while (r.hasNext()) {
                    if (r.next() != XMLStreamConstants.START_ELEMENT) {
                        continue;
                    }
                    String nomeElemento = r.getLocalName();
                    if ("workbookPr".equals(nomeElemento)) {
                        String d1904 = r.getAttributeValue(null, "date1904");
                        base1904 = "1".equals(d1904) || "true".equalsIgnoreCase(d1904);
                    } else if ("sheet".equals(nomeElemento)) {
                        String nome = r.getAttributeValue(null, "name");
                        String id = null;
                        for (int i = 0; i < r.getAttributeCount(); i++) {
                            if ("id".equals(r.getAttributeLocalName(i))) {
                                id = r.getAttributeValue(i);
                            }
                        }
                        semRel++;
                        String caminho = id == null ? null : alvoPorId.get(id);
                        if (caminho == null) {
                            caminho = "xl/worksheets/sheet" + semRel + ".xml";
                        }
                        if (nome != null) {
                            abas.put(nome, caminho);
                        }
                    }
                }
            } catch (XMLStreamException e) {
                throw new IOException("xl/workbook.xml ilegivel.", e);
            } finally {
                fechar(r, wb);
            }
        }

        private static String normalizarAlvo(String alvo) {
            String limpo = alvo.replace('\\', '/');
            if (limpo.startsWith("/")) {
                return limpo.substring(1);
            }
            while (limpo.startsWith("../")) {
                limpo = limpo.substring(3);
            }
            return limpo.startsWith("xl/") ? limpo : "xl/" + limpo;
        }

        /**
         * O Excel guarda o texto em sharedStrings.xml (t="s"); o openpyxl grava
         * inlineStr direto na celula. Os dois caminhos tem de funcionar.
         */
        private void lerTextosCompartilhados() throws IOException {
            InputStream in = parte("xl/sharedStrings.xml");
            if (in == null) {
                return;
            }
            XMLStreamReader r = abrir(in);
            try {
                StringBuilder atual = null;
                boolean dentroDeFonetica = false;
                while (r.hasNext()) {
                    int evento = r.next();
                    if (evento == XMLStreamConstants.START_ELEMENT) {
                        String nomeElemento = r.getLocalName();
                        if ("si".equals(nomeElemento)) {
                            atual = new StringBuilder();
                        } else if ("rPh".equals(nomeElemento)) {
                            dentroDeFonetica = true;
                        } else if ("t".equals(nomeElemento) && atual != null && !dentroDeFonetica) {
                            atual.append(r.getElementText());
                        }
                    } else if (evento == XMLStreamConstants.END_ELEMENT) {
                        String nomeElemento = r.getLocalName();
                        if ("rPh".equals(nomeElemento)) {
                            dentroDeFonetica = false;
                        } else if ("si".equals(nomeElemento) && atual != null) {
                            textos.add(atual.toString());
                            atual = null;
                        }
                    }
                }
            } catch (XMLStreamException e) {
                throw new IOException("xl/sharedStrings.xml ilegivel.", e);
            } finally {
                fechar(r, in);
            }
        }

        private void lerEstilos() throws IOException {
            InputStream in = parte("xl/styles.xml");
            if (in == null) {
                return;
            }
            XMLStreamReader r = abrir(in);
            try {
                boolean dentroCellXfs = false;
                while (r.hasNext()) {
                    int evento = r.next();
                    if (evento == XMLStreamConstants.START_ELEMENT) {
                        String nomeElemento = r.getLocalName();
                        if ("cellXfs".equals(nomeElemento)) {
                            dentroCellXfs = true;
                        } else if ("numFmt".equals(nomeElemento)) {
                            String id = r.getAttributeValue(null, "numFmtId");
                            String codigo = r.getAttributeValue(null, "formatCode");
                            if (id != null && codigo != null) {
                                try {
                                    formatosPersonalizados.put(Integer.valueOf(id), codigo);
                                } catch (NumberFormatException e) {
                                    LOG.log(Level.FINE, "numFmtId invalido no styles.xml", e);
                                }
                            }
                        } else if (dentroCellXfs && "xf".equals(nomeElemento)) {
                            String id = r.getAttributeValue(null, "numFmtId");
                            int valor = 0;
                            if (id != null) {
                                try {
                                    valor = Integer.parseInt(id);
                                } catch (NumberFormatException e) {
                                    valor = 0;
                                }
                            }
                            formatoPorEstilo.add(Integer.valueOf(valor));
                        }
                    } else if (evento == XMLStreamConstants.END_ELEMENT
                            && "cellXfs".equals(r.getLocalName())) {
                        dentroCellXfs = false;
                    }
                }
            } catch (XMLStreamException e) {
                throw new IOException("xl/styles.xml ilegivel.", e);
            } finally {
                fechar(r, in);
            }
        }

        // -------- leitura de uma aba

        /**
         * Acha a aba pelo nome. Se nao bater exatamente, tenta ignorando
         * maiusculas/minusculas e espacos: a planilha real tinha "PRINCIPAL" e
         * o config pedia "Principal" - era so isso que derrubava a geracao.
         */
        String nomeRealDaAba(String procurado) {
            if (abas.containsKey(procurado)) {
                return procurado;
            }
            String alvo = procurado == null ? "" : procurado.trim();
            for (Map.Entry<String, String> e : abas.entrySet()) {
                if (e.getKey().trim().equalsIgnoreCase(alvo)) {
                    avisos.add("A aba \"" + procurado + "\" do config.properties aparece na planilha como \""
                            + e.getKey() + "\". Usei essa.");
                    return e.getKey();
                }
            }
            throw new IllegalStateException("A aba \"" + procurado + "\" nao existe na planilha."
                    + System.lineSeparator() + "Abas encontradas: " + juntar(nomesDasAbas(), ", "));
        }

        Aba lerAba(String procurado) throws IOException {
            String nome = nomeRealDaAba(procurado);
            Aba aba = new Aba(nome);
            InputStream in = parte(abas.get(nome));
            if (in == null) {
                throw new IllegalStateException("A aba \"" + nome + "\" esta no indice da planilha, "
                        + "mas o conteudo dela nao esta no arquivo.");
            }
            XMLStreamReader r = abrir(in);
            try {
                int linhaAtual = 0;
                int colunaAtual = 0;
                while (r.hasNext()) {
                    if (r.next() != XMLStreamConstants.START_ELEMENT) {
                        continue;
                    }
                    String nomeElemento = r.getLocalName();
                    if ("row".equals(nomeElemento)) {
                        String ref = r.getAttributeValue(null, "r");
                        linhaAtual = ref == null ? linhaAtual + 1 : inteiroOu(ref, linhaAtual + 1);
                        colunaAtual = 0;
                        if (linhaAtual > aba.ultimaLinha) {
                            aba.ultimaLinha = linhaAtual;
                        }
                    } else if ("c".equals(nomeElemento)) {
                        String ref = r.getAttributeValue(null, "r");
                        if (ref == null) {
                            colunaAtual++;
                            ref = indiceParaColuna(colunaAtual) + linhaAtual;
                        } else {
                            colunaAtual = colunaParaIndice(soLetras(ref));
                        }
                        String valor = lerCelula(r, nome, ref);
                        if (!vazio(valor)) {
                            aba.celulas.put(ref.toUpperCase(), valor);
                        }
                    }
                }
            } catch (XMLStreamException e) {
                throw new IOException("A aba \"" + nome + "\" esta ilegivel.", e);
            } finally {
                fechar(r, in);
            }
            return aba;
        }

        /** Le <c> ate o </c>, devolvendo o valor bruto - nunca a mascara. */
        private String lerCelula(XMLStreamReader r, String nomeAba, String referencia)
                throws XMLStreamException {
            String tipo = r.getAttributeValue(null, "t");
            String estilo = r.getAttributeValue(null, "s");
            boolean formula = false;
            boolean temValor = false;
            String bruto = "";
            String textoInline = null;

            int profundidade = 1;
            while (r.hasNext() && profundidade > 0) {
                int evento = r.next();
                if (evento == XMLStreamConstants.START_ELEMENT) {
                    String nomeElemento = r.getLocalName();
                    if ("v".equals(nomeElemento)) {
                        bruto = r.getElementText();
                        temValor = true;
                    } else if ("f".equals(nomeElemento)) {
                        formula = true;
                        pularSubarvore(r);
                    } else if ("is".equals(nomeElemento)) {
                        textoInline = lerTextoInline(r);
                    } else {
                        profundidade++;
                    }
                } else if (evento == XMLStreamConstants.END_ELEMENT) {
                    profundidade--;
                }
            }

            if (textoInline != null) {
                return textoInline;
            }
            if (formula && (!temValor || vazio(bruto))) {
                // Formula que devolve texto vazio de proposito vem com t="str":
                // isso e resultado, nao falta de calculo. Nao e aviso.
                if (!"str".equals(tipo)) {
                    avisos.add("A celula " + referencia + " da aba \"" + nomeAba
                            + "\" tem formula sem valor calculado. Abra a planilha, deixe o Excel calcular"
                            + " e salve - o programa so le o que ja esta gravado.");
                }
                return "";
            }
            if (!temValor) {
                return "";
            }
            if ("s".equals(tipo)) {
                int indice = inteiroOu(bruto, -1);
                if (indice < 0 || indice >= textos.size()) {
                    avisos.add("A celula " + referencia + " da aba \"" + nomeAba
                            + "\" aponta para um texto que nao esta na planilha.");
                    return "";
                }
                return textos.get(indice);
            }
            if ("str".equals(tipo) || "inlineStr".equals(tipo) || "e".equals(tipo)) {
                return bruto;
            }
            if ("b".equals(tipo)) {
                return "1".equals(bruto) ? "VERDADEIRO" : "FALSO";
            }
            return formatarNumerico(bruto, estilo);
        }

        private static String lerTextoInline(XMLStreamReader r) throws XMLStreamException {
            StringBuilder sb = new StringBuilder();
            boolean dentroDeFonetica = false;
            int profundidade = 1;
            while (r.hasNext() && profundidade > 0) {
                int evento = r.next();
                if (evento == XMLStreamConstants.START_ELEMENT) {
                    String nomeElemento = r.getLocalName();
                    if ("rPh".equals(nomeElemento)) {
                        dentroDeFonetica = true;
                        profundidade++;
                    } else if ("t".equals(nomeElemento) && !dentroDeFonetica) {
                        sb.append(r.getElementText());
                    } else {
                        profundidade++;
                    }
                } else if (evento == XMLStreamConstants.END_ELEMENT) {
                    if ("rPh".equals(r.getLocalName())) {
                        dentroDeFonetica = false;
                    }
                    profundidade--;
                }
            }
            return sb.toString();
        }

        private static void pularSubarvore(XMLStreamReader r) throws XMLStreamException {
            int profundidade = 1;
            while (r.hasNext() && profundidade > 0) {
                int evento = r.next();
                if (evento == XMLStreamConstants.START_ELEMENT) {
                    profundidade++;
                } else if (evento == XMLStreamConstants.END_ELEMENT) {
                    profundidade--;
                }
            }
        }

        /** Numero cru como o VBA entregava: 1234,5 (e nao a mascara 1.234,50). */
        private String formatarNumerico(String bruto, String estilo) {
            if (vazio(bruto)) {
                return "";
            }
            double numero;
            try {
                numero = Double.parseDouble(bruto);
            } catch (NumberFormatException e) {
                return bruto;
            }
            if (ehData(estilo)) {
                return formatarData(numero);
            }
            return formatarNumero(numero);
        }

        private boolean ehData(String estilo) {
            if (estilo == null) {
                return false;
            }
            int indice = inteiroOu(estilo, -1);
            if (indice < 0 || indice >= formatoPorEstilo.size()) {
                return false;
            }
            Integer formato = formatoPorEstilo.get(indice);
            if (FORMATOS_DATA.contains(formato)) {
                return true;
            }
            String codigo = formatosPersonalizados.get(formato);
            return codigo != null && codigoEhData(codigo);
        }

        private String formatarData(double serial) {
            LocalDate base = base1904 ? LocalDate.of(1904, 1, 1) : LocalDate.of(1899, 12, 30);
            long dias = (long) Math.floor(serial);
            double fracao = serial - dias;
            if (!base1904 && dias < 60) {
                // O Excel finge que 1900 teve 29 de fevereiro; antes disso a
                // contagem anda um dia para tras.
                dias = dias + 1;
            }
            LocalDate data = base.plusDays(dias);
            long segundos = Math.round(fracao * 86400.0d);
            if (segundos <= 0 || segundos >= 86400) {
                return data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
            LocalDateTime dataHora = data.atStartOfDay().plusSeconds(segundos);
            return dataHora.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        }

        private static boolean codigoEhData(String codigo) {
            StringBuilder limpo = new StringBuilder();
            boolean entreAspas = false;
            boolean entreColchetes = false;
            for (int i = 0; i < codigo.length(); i++) {
                char c = codigo.charAt(i);
                if (c == '"') {
                    entreAspas = !entreAspas;
                } else if (c == '[') {
                    entreColchetes = true;
                } else if (c == ']') {
                    entreColchetes = false;
                } else if (c == '\\') {
                    i++;
                } else if (!entreAspas && !entreColchetes) {
                    limpo.append(Character.toLowerCase(c));
                }
            }
            String texto = limpo.toString();
            if (texto.indexOf('@') >= 0) {
                return false;
            }
            return texto.indexOf('y') >= 0 || texto.indexOf('d') >= 0
                    || texto.indexOf('h') >= 0 || texto.indexOf('s') >= 0;
        }

        private static Set<Integer> formatosData() {
            Set<Integer> ids = new HashSet<Integer>();
            int[] fixos = {14, 15, 16, 17, 18, 19, 20, 21, 22, 27, 28, 29, 30, 31, 32, 33, 34, 35,
                    36, 45, 46, 47, 50, 51, 52, 53, 54, 55, 56, 57, 58};
            for (int i = 0; i < fixos.length; i++) {
                ids.add(Integer.valueOf(fixos[i]));
            }
            return Collections.unmodifiableSet(ids);
        }

        private static XMLStreamReader abrir(InputStream in) throws IOException {
            try {
                XMLInputFactory fabrica = XMLInputFactory.newInstance();
                fabrica.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
                fabrica.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
                fabrica.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
                return fabrica.createXMLStreamReader(in);
            } catch (XMLStreamException e) {
                throw new IOException("Nao consegui ler o XML interno da planilha.", e);
            }
        }

        private static void fechar(XMLStreamReader r, InputStream in) {
            try {
                r.close();
            } catch (XMLStreamException e) {
                LOG.log(Level.FINE, "falha ao fechar o leitor de XML", e);
            }
            try {
                in.close();
            } catch (IOException e) {
                LOG.log(Level.FINE, "falha ao fechar a parte da planilha", e);
            }
        }
    }

    // ------------------------------------------------------------------
    // geracao
    // ------------------------------------------------------------------

    static final class Resultado {
        final File arquivo;
        final int registros;
        final int bytes;
        final List<String> avisos;

        Resultado(File arquivo, int registros, int bytes, List<String> avisos) {
            this.arquivo = arquivo;
            this.registros = registros;
            this.bytes = bytes;
            this.avisos = avisos;
        }
    }

    /** Erro de dado da planilha: a mensagem ja esta pronta para o usuario. */
    static final class ErroDeDados extends IOException {
        private static final long serialVersionUID = 1L;

        ErroDeDados(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }
    }

    /** Perguntado quando o txt ja existe e saida.sobrescrever=perguntar. */
    interface Confirmacao {
        boolean sobrescrever(File arquivo);
    }

    static Resultado gerar(Config cfg, String destinoEscolhido, Confirmacao confirmacao) throws IOException {
        List<String> avisos = new ArrayList<String>();
        if (vazio(cfg.planilhaCaminho)) {
            throw new IllegalStateException("Escolha a planilha (ou preencha planilha.caminho no "
                    + NOME_CONFIG + ").");
        }
        File planilha = new File(cfg.planilhaCaminho);
        LeitorPlanilha leitor = new LeitorPlanilha(planilha, avisos);
        List<String[]> registros;
        File destino;
        try {
            Aba principal = leitor.lerAba(cfg.abaPrincipal);
            conferirObrigatorias(cfg, principal);
            destino = destinoFinal(cfg, principal, destinoEscolhido);
            registros = lerRegistros(cfg, leitor.lerAba(cfg.abaBase), avisos);
        } finally {
            leitor.fechar();
        }
        if (registros.isEmpty()) {
            throw new IllegalStateException("A aba \"" + cfg.abaBase + "\" nao tem nenhuma linha preenchida"
                    + " a partir da linha " + cfg.linhaInicial + ".");
        }

        conferirExistente(cfg, destino, confirmacao);
        byte[] conteudo = montarConteudo(cfg, registros, avisos);

        File pasta = destino.getAbsoluteFile().getParentFile();
        if (pasta != null && !pasta.isDirectory() && !pasta.mkdirs()) {
            throw new IOException("Nao consegui criar a pasta " + pasta.getAbsolutePath());
        }
        Files.write(destino.toPath(), conteudo);
        cfg.gravarDestino(destino.getAbsolutePath());

        LOG.info("planilha=" + cfg.planilhaCaminho + " registros=" + registros.size()
                + " arquivo=" + destino.getAbsolutePath() + " bytes=" + conteudo.length
                + " avisos=" + avisos.size());
        return new Resultado(destino, registros.size(), conteudo.length, avisos);
    }

    private static void conferirObrigatorias(Config cfg, Aba principal) {
        List<String> faltando = new ArrayList<String>();
        for (int i = 0; i < cfg.celulasObrigatorias.size(); i++) {
            String ref = cfg.celulasObrigatorias.get(i);
            if (vazio(principal.valor(ref))) {
                faltando.add(ref);
            }
        }
        if (!faltando.isEmpty()) {
            throw new IllegalStateException("Preencha " + juntar(faltando, " e ") + " na aba \""
                    + principal.nome + "\" antes de gerar o arquivo.");
        }
    }

    private static File destinoFinal(Config cfg, Aba principal, String escolhido) {
        String caminho = escolhido == null ? "" : escolhido.trim();
        if (!vazio(caminho)) {
            return comExtensao(new File(caminho), cfg.extensao);
        }
        String pasta = principal.valor(cfg.celulaPasta).trim();
        String nome = principal.valor(cfg.celulaNome).trim();
        if (vazio(pasta) || vazio(nome)) {
            throw new IllegalStateException("Escolha onde salvar o txt, ou preencha a pasta ("
                    + cfg.celulaPasta + ") e o nome (" + cfg.celulaNome + ") na aba \""
                    + principal.nome + "\".");
        }
        return comExtensao(new File(pasta, nome), cfg.extensao);
    }

    private static File comExtensao(File arquivo, String extensao) {
        if (vazio(extensao) || arquivo.getName().toLowerCase().endsWith(extensao.toLowerCase())) {
            return arquivo;
        }
        return new File(arquivo.getParentFile(), arquivo.getName() + extensao);
    }

    private static void conferirExistente(Config cfg, File destino, Confirmacao confirmacao) {
        if (!destino.exists()) {
            return;
        }
        if ("sempre".equals(cfg.sobrescrever)) {
            return;
        }
        if ("perguntar".equals(cfg.sobrescrever) && confirmacao != null
                && confirmacao.sobrescrever(destino)) {
            return;
        }
        throw new IllegalStateException("O arquivo " + destino.getAbsolutePath()
                + " ja existe. Voce deve exclui-lo para gravar.");
    }

    /** Varre a aba Base montando um registro por linha preenchida. */
    private static List<String[]> lerRegistros(Config cfg, Aba base, List<String> avisos) {
        int primeira = colunaParaIndice(cfg.colunaInicial);
        int ultima = colunaParaIndice(cfg.colunaFinal);
        List<String[]> registros = new ArrayList<String[]>();
        for (int linha = cfg.linhaInicial; linha <= base.ultimaLinha; linha++) {
            String[] campos = new String[ultima - primeira + 1];
            boolean vaziaToda = true;
            for (int coluna = primeira; coluna <= ultima; coluna++) {
                String valor = base.valor(indiceParaColuna(coluna) + linha);
                campos[coluna - primeira] = valor;
                if (!vazio(valor)) {
                    vaziaToda = false;
                }
            }
            boolean primeiraVazia = vazio(campos[0]);
            if (cfg.pararNaLinhaVazia && primeiraVazia) {
                break;
            }
            if (vaziaToda) {
                continue;
            }
            if (primeiraVazia) {
                avisos.add("A linha " + linha + " da aba \"" + base.nome + "\" tem dados, mas a coluna "
                        + cfg.colunaInicial + " esta vazia. Gravei assim mesmo - confira.");
            }
            registros.add(campos);
        }
        return registros;
    }

    private static byte[] montarConteudo(Config cfg, List<String[]> registros, List<String> avisos)
            throws IOException {
        String quebra = cfg.quebra();
        String cabecalho = cfg.prefixoCabecalho + cfg.separador
                + juntar(Arrays.asList(cfg.camposCabecalho.split(",", -1)), cfg.separador) + cfg.separador;

        CharsetEncoder encoder = Charset.forName(cfg.codificacao).newEncoder();
        encoder.onMalformedInput(CodingErrorAction.REPORT);
        encoder.onUnmappableCharacter(CodingErrorAction.REPORT);

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        if (cfg.linhaEmBrancoNoInicio) {
            saida.write(codificar(encoder, quebra, 0, cfg));
        }
        for (int i = 0; i < registros.size(); i++) {
            String[] campos = registros.get(i);
            int linha = cfg.linhaInicial + i;
            saida.write(codificar(encoder, cabecalho + quebra, linha, cfg));
            StringBuilder sb = new StringBuilder(cfg.prefixoDetalhe);
            for (int c = 0; c < campos.length; c++) {
                sb.append(cfg.separador).append(limpar(campos[c], cfg, linha,
                        indiceParaColuna(colunaParaIndice(cfg.colunaInicial) + c), avisos));
            }
            sb.append(cfg.separador).append(quebra);
            saida.write(codificar(encoder, sb.toString(), linha, cfg));
        }
        return saida.toByteArray();
    }

    /** O separador dentro do dado quebraria o layout: troca e avisa, nunca escondido. */
    private static String limpar(String valor, Config cfg, int linha, String coluna, List<String> avisos) {
        if (valor == null) {
            return "";
        }
        String limpo = valor.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
        if (limpo.indexOf(cfg.separador) >= 0) {
            avisos.add("A celula " + coluna + linha + " da aba Base tem o separador \"" + cfg.separador
                    + "\" no meio do texto. Troquei por \"" + cfg.substitutoSeparador + "\".");
            limpo = limpo.replace(cfg.separador, cfg.substitutoSeparador);
        }
        return limpo;
    }

    private static byte[] codificar(CharsetEncoder encoder, String texto, int linha, Config cfg)
            throws IOException {
        try {
            encoder.reset();
            ByteBuffer buffer = encoder.encode(CharBuffer.wrap(texto));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        } catch (CharacterCodingException e) {
            throw new ErroDeDados("A linha " + linha + " tem um caractere que nao existe em "
                    + cfg.codificacao + " (" + primeiroForaDaTabela(encoder, texto) + "). "
                    + "Corrija o texto na planilha.", e);
        }
    }

    private static String primeiroForaDaTabela(CharsetEncoder encoder, String texto) {
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (!encoder.canEncode(c)) {
                return "\"" + c + "\", posicao " + (i + 1);
            }
        }
        return "caractere nao identificado";
    }

    // ------------------------------------------------------------------
    // janela
    // ------------------------------------------------------------------

    private static void abrirJanela(final File arquivoConfig) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e) {
                    LOG.log(Level.FINE, "look and feel do sistema indisponivel", e);
                }
                new Janela(arquivoConfig).setVisible(true);
            }
        });
    }

    static final class Janela extends JFrame {

        private static final long serialVersionUID = 1L;

        private final File arquivoConfig;
        private final JTextField campoPlanilha = new JTextField(34);
        private final JTextField campoDestino = new JTextField(34);
        private final JLabel rotuloAbas = new JLabel(" ");
        private final JTextArea areaLog = new JTextArea();
        private final JButton botaoGerar = new JButton("Gerar arquivo");
        private final JButton botaoPasta = new JButton("Abrir pasta");
        private final JButton botaoRecarregar = new JButton("Recarregar config");
        private transient Config cfg;
        private File ultimoArquivo;

        Janela(File arquivoConfig) {
            super("Gerador de Arquivo TXT");
            this.arquivoConfig = arquivoConfig;
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLayout(new BorderLayout(0, 0));
            add(montarTopo(), BorderLayout.NORTH);
            add(montarLog(), BorderLayout.CENTER);
            add(montarRodape(), BorderLayout.SOUTH);
            ligarBotoes();
            escrever("Gerador de Arquivo TXT v" + VERSAO);
            carregarConfig();
            pack();
            setMinimumSize(new Dimension(680, 520));
            setSize(new Dimension(Math.min(Math.max(getWidth(), 800), 980), Math.max(getHeight(), 560)));
            setLocationRelativeTo(null);
        }

        private JPanel montarTopo() {
            JPanel painel = new JPanel(new GridBagLayout());
            painel.setBorder(BorderFactory.createEmptyBorder(12, 14, 6, 14));
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(2, 2, 2, 2);
            g.anchor = GridBagConstraints.WEST;

            JLabel titulo = new JLabel("Gerador de Arquivo TXT");
            titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 20f));
            g.gridx = 0;
            g.gridy = 0;
            g.gridwidth = 3;
            painel.add(titulo, g);

            JLabel subtitulo = new JLabel("layout 6000/6100  |  substitui a macro lDom");
            subtitulo.setForeground(Color.GRAY);
            g.gridy = 1;
            painel.add(subtitulo, g);

            g.gridwidth = 1;
            g.gridy = 2;
            g.gridx = 0;
            g.insets = new Insets(12, 2, 2, 8);
            painel.add(negrito(new JLabel("Planilha:")), g);
            g.insets = new Insets(12, 2, 2, 2);
            g.gridx = 1;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.weightx = 1;
            painel.add(campoPlanilha, g);
            g.fill = GridBagConstraints.NONE;
            g.weightx = 0;
            g.gridx = 2;
            JButton escolherPlanilha = new JButton("Selecionar...");
            painel.add(escolherPlanilha, g);

            g.insets = new Insets(2, 2, 2, 8);
            g.gridy = 3;
            g.gridx = 0;
            painel.add(negrito(new JLabel("Salvar txt em:")), g);
            g.insets = new Insets(2, 2, 2, 2);
            g.gridx = 1;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.weightx = 1;
            painel.add(campoDestino, g);
            g.fill = GridBagConstraints.NONE;
            g.weightx = 0;
            g.gridx = 2;
            JButton escolherDestino = new JButton("Selecionar...");
            painel.add(escolherDestino, g);

            JLabel dica = new JLabel("em branco = usa a pasta (B9) e o nome (B10) da aba Principal");
            dica.setForeground(Color.GRAY);
            g.gridy = 4;
            g.gridx = 1;
            g.gridwidth = 2;
            painel.add(dica, g);

            g.gridwidth = 1;
            g.gridy = 5;
            g.gridx = 0;
            g.insets = new Insets(8, 2, 2, 8);
            painel.add(negrito(new JLabel("Abas:")), g);
            g.gridx = 1;
            g.gridwidth = 2;
            g.insets = new Insets(8, 2, 2, 2);
            rotuloAbas.setForeground(new Color(0, 60, 140));
            painel.add(negrito(rotuloAbas), g);

            JPanel botoes = new JPanel(new GridBagLayout());
            GridBagConstraints b = new GridBagConstraints();
            b.insets = new Insets(0, 0, 0, 6);
            b.gridy = 0;
            b.gridx = 0;
            botoes.add(botaoGerar, b);
            b.gridx = 1;
            botoes.add(botaoPasta, b);
            b.gridx = 2;
            botoes.add(botaoRecarregar, b);
            g.gridy = 6;
            g.gridx = 0;
            g.gridwidth = 3;
            g.insets = new Insets(12, 0, 2, 2);
            painel.add(botoes, g);

            escolherPlanilha.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    escolherPlanilha();
                }
            });
            escolherDestino.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    escolherDestino();
                }
            });
            botaoPasta.setEnabled(false);
            return painel;
        }

        private static JLabel negrito(JLabel rotulo) {
            rotulo.setFont(rotulo.getFont().deriveFont(Font.BOLD));
            return rotulo;
        }

        private JScrollPane montarLog() {
            areaLog.setEditable(false);
            areaLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            areaLog.setLineWrap(true);
            areaLog.setWrapStyleWord(true);
            JScrollPane rolagem = new JScrollPane(areaLog);
            rolagem.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
            rolagem.setPreferredSize(new Dimension(640, 260));
            return rolagem;
        }

        private JPanel montarRodape() {
            JPanel painel = new JPanel(new BorderLayout());
            painel.setBorder(BorderFactory.createEmptyBorder(2, 14, 10, 14));
            String pasta = arquivoConfig.getAbsoluteFile().getParent();
            JLabel rotulo = new JLabel(NOME_CONFIG + " e " + NOME_LOG + " ficam em: " + pasta);
            rotulo.setForeground(Color.DARK_GRAY);
            rotulo.setToolTipText(pasta);
            // um caminho comprido nao pode esticar a janela inteira
            rotulo.setMinimumSize(new Dimension(1, rotulo.getPreferredSize().height));
            rotulo.setPreferredSize(new Dimension(1, rotulo.getPreferredSize().height));
            painel.add(rotulo, BorderLayout.CENTER);
            return painel;
        }

        private void ligarBotoes() {
            botaoGerar.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    gerarAgora();
                }
            });
            botaoRecarregar.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    carregarConfig();
                }
            });
            botaoPasta.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    abrirPasta();
                }
            });
        }

        private void carregarConfig() {
            try {
                cfg = Config.carregar(arquivoConfig);
                campoPlanilha.setText(cfg.planilhaCaminho);
                campoDestino.setText(cfg.saidaDestino);
                escrever("config lido de " + cfg.arquivo.getAbsolutePath());
                mostrarAbas();
                botaoGerar.setEnabled(true);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "falha ao ler o config", e);
                escrever("ERRO: " + mensagem(e));
                botaoGerar.setEnabled(false);
            }
        }

        private void mostrarAbas() {
            String caminho = campoPlanilha.getText().trim();
            if (vazio(caminho) || !new File(caminho).isFile()) {
                rotuloAbas.setText("planilha nao encontrada");
                return;
            }
            List<String> avisos = new ArrayList<String>();
            try {
                LeitorPlanilha leitor = new LeitorPlanilha(new File(caminho), avisos);
                try {
                    rotuloAbas.setText(juntar(leitor.nomesDasAbas(), ", "));
                } finally {
                    leitor.fechar();
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "falha ao listar as abas", e);
                rotuloAbas.setText("nao consegui ler a planilha");
            }
        }

        private void escolherPlanilha() {
            JFileChooser seletor = new JFileChooser();
            seletor.setDialogTitle("Escolha a planilha");
            seletor.setFileFilter(new FileNameExtensionFilter("Planilha do Excel (*.xlsx, *.xlsm)",
                    "xlsx", "xlsm"));
            String atual = campoPlanilha.getText().trim();
            if (!vazio(atual)) {
                File arquivo = new File(atual);
                seletor.setCurrentDirectory(arquivo.getParentFile());
            }
            if (seletor.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                campoPlanilha.setText(seletor.getSelectedFile().getAbsolutePath());
                mostrarAbas();
            }
        }

        private void escolherDestino() {
            JFileChooser seletor = new JFileChooser();
            seletor.setDialogTitle("Onde salvar o txt");
            String atual = campoDestino.getText().trim();
            if (!vazio(atual)) {
                seletor.setSelectedFile(new File(atual));
            }
            if (seletor.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                campoDestino.setText(seletor.getSelectedFile().getAbsolutePath());
            }
        }

        private void gerarAgora() {
            if (cfg == null) {
                carregarConfig();
                if (cfg == null) {
                    return;
                }
            }
            cfg.planilhaCaminho = campoPlanilha.getText().trim();
            final String destino = campoDestino.getText().trim();
            botaoGerar.setEnabled(false);
            escrever("gerando...");
            new SwingWorker<Resultado, Void>() {
                protected Resultado doInBackground() throws Exception {
                    return gerar(cfg, destino, new Confirmacao() {
                        public boolean sobrescrever(File arquivo) {
                            return perguntarSobrescrever(arquivo);
                        }
                    });
                }

                protected void done() {
                    botaoGerar.setEnabled(true);
                    try {
                        Resultado r = get();
                        for (int i = 0; i < r.avisos.size(); i++) {
                            escrever("AVISO: " + r.avisos.get(i));
                        }
                        escrever("pronto: " + r.registros + " registros, " + r.bytes + " bytes");
                        escrever("arquivo: " + r.arquivo.getAbsolutePath());
                        campoDestino.setText(r.arquivo.getAbsolutePath());
                        ultimoArquivo = r.arquivo;
                        botaoPasta.setEnabled(true);
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, "falha ao gerar", e);
                        escrever("ERRO: " + mensagem(e));
                        JOptionPane.showMessageDialog(Janela.this, mensagem(e),
                                "Gerador de Arquivo TXT", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }

        private boolean perguntarSobrescrever(File arquivo) {
            final boolean[] resposta = new boolean[1];
            Runnable pergunta = new Runnable() {
                public void run() {
                    int escolha = JOptionPane.showConfirmDialog(Janela.this,
                            "O arquivo ja existe:" + System.lineSeparator() + arquivo.getAbsolutePath()
                                    + System.lineSeparator() + System.lineSeparator() + "Sobrescrever?",
                            "Gerador de Arquivo TXT", JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE);
                    resposta[0] = escolha == JOptionPane.YES_OPTION;
                }
            };
            try {
                if (SwingUtilities.isEventDispatchThread()) {
                    pergunta.run();
                } else {
                    SwingUtilities.invokeAndWait(pergunta);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "falha ao perguntar sobre sobrescrever", e);
                return false;
            }
            return resposta[0];
        }

        private void abrirPasta() {
            if (ultimoArquivo == null) {
                return;
            }
            File pasta = ultimoArquivo.getAbsoluteFile().getParentFile();
            try {
                if (pasta != null && Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(pasta);
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "falha ao abrir a pasta", e);
                escrever("ERRO: nao consegui abrir " + pasta);
            }
        }

        private void escrever(String linha) {
            areaLog.append(linha + System.lineSeparator());
            areaLog.setCaretPosition(areaLog.getDocument().getLength());
        }
    }

    // ------------------------------------------------------------------
    // utilitarios
    // ------------------------------------------------------------------

    /** Java 8 nao tem String.isBlank. */
    static boolean vazio(String texto) {
        return texto == null || texto.trim().isEmpty();
    }

    static String juntar(List<String> partes, String separador) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.size(); i++) {
            if (i > 0) {
                sb.append(separador);
            }
            sb.append(partes.get(i));
        }
        return sb.toString();
    }

    static String mensagem(Throwable e) {
        String texto = e.getMessage();
        if (vazio(texto)) {
            texto = e.getClass().getSimpleName();
        }
        if (e instanceof ErroDeDados) {
            return texto;
        }
        Throwable causa = e.getCause();
        if (causa != null && !vazio(causa.getMessage()) && texto.indexOf(causa.getMessage()) < 0) {
            texto = texto + " (" + causa.getMessage() + ")";
        }
        return texto;
    }

    /** "A" -> 1, "I" -> 9, "AA" -> 27. */
    static int colunaParaIndice(String coluna) {
        int indice = 0;
        String limpo = coluna == null ? "" : coluna.trim().toUpperCase();
        for (int i = 0; i < limpo.length(); i++) {
            char c = limpo.charAt(i);
            if (c < 'A' || c > 'Z') {
                throw new IllegalStateException("Coluna invalida: \"" + coluna + "\".");
            }
            indice = indice * 26 + (c - 'A' + 1);
        }
        if (indice == 0) {
            throw new IllegalStateException("Coluna invalida: \"" + coluna + "\".");
        }
        return indice;
    }

    static String indiceParaColuna(int indice) {
        StringBuilder sb = new StringBuilder();
        int atual = indice;
        while (atual > 0) {
            int resto = (atual - 1) % 26;
            sb.insert(0, (char) ('A' + resto));
            atual = (atual - 1) / 26;
        }
        return sb.toString();
    }

    private static String soLetras(String referencia) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < referencia.length(); i++) {
            char c = referencia.charAt(i);
            if (Character.isLetter(c)) {
                sb.append(c);
            } else {
                break;
            }
        }
        return sb.toString();
    }

    private static int inteiroOu(String texto, int padrao) {
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            return padrao;
        } catch (NullPointerException e) {
            return padrao;
        }
    }

    /**
     * Numero como o VBA gravava: virgula decimal, sem separador de milhar e sem
     * zeros a direita sobrando. 1234.5 -> "1234,5"; 1000.0 -> "1000".
     */
    static String formatarNumero(double numero) {
        BigDecimal valor = new BigDecimal(Double.toString(numero)).stripTrailingZeros();
        if (valor.scale() <= 0) {
            return valor.toBigInteger().toString();
        }
        return valor.toPlainString().replace('.', ',');
    }
}
