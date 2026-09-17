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
import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
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

    static final String VERSAO = "3.4.0";
    static final String AUTOR = "Ronald Lira";
    static final String EMPRESA = "Triangulo Contabilidade";
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
        System.out.println("Gerador de Arquivo TXT v" + VERSAO + " - " + AUTOR);
        System.out.println("  (sem argumento)     abre a janela");
        System.out.println("  --console           gera o txt sem janela, usando o config.properties");
        System.out.println("  --config <arquivo>  usa outro config.properties");
    }

    private static int executarConsole(File arquivoConfig) {
        try {
            Config cfg = Config.carregar(arquivoConfig);
            System.out.println("Gerador de Arquivo TXT v" + VERSAO + " - " + AUTOR + " (console)");
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

        // -------- paleta da tela (mexer aqui muda a cara do programa)
        private static final Color AZUL_ESCURO = new Color(16, 46, 84);
        private static final Color AZUL = new Color(29, 91, 154);
        private static final Color AZUL_CLARO = new Color(176, 205, 232);
        private static final Color FUNDO = new Color(247, 249, 252);
        private static final Color CINZA_TEXTO = new Color(95, 105, 118);
        private static final Color VERDE = new Color(0, 116, 62);
        private static final Color VERDE_FUNDO = new Color(232, 244, 236);
        private static final Color LARANJA = new Color(181, 101, 0);
        private static final Color LARANJA_FUNDO = new Color(255, 246, 229);
        private static final Color VERMELHO = new Color(176, 28, 28);
        private static final Color VERMELHO_FUNDO = new Color(253, 235, 235);

        private final File arquivoConfig;
        private final JTextField campoPlanilha = new JTextField(28);
        private final JTextField campoDestino = new JTextField(28);
        private final JLabel rotuloAbas = new JLabel(" ");
        private final JTextPane registro = new JTextPane();
        private final JProgressBar barra = new JProgressBar();
        private final JLabel rotuloEstado = new JLabel("pronto");
        private final JButton botaoGerar = new JButton("Gerar arquivo");
        private final JButton botaoTxt = new JButton("Abrir txt");
        private final JButton botaoPasta = new JButton("Abrir pasta");
        private final JButton botaoRecarregar = new JButton("Recarregar config");
        private transient Config cfg;
        private File ultimoArquivo;

        Janela(File arquivoConfig) {
            super("Gerador de Arquivo TXT v" + VERSAO + " - by " + AUTOR);
            this.arquivoConfig = arquivoConfig;
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            JPanel miolo = new JPanel(new BorderLayout());
            miolo.setBackground(FUNDO);
            miolo.add(montarFaixa(), BorderLayout.NORTH);
            miolo.add(montarAbas(), BorderLayout.CENTER);
            miolo.add(montarRodape(), BorderLayout.SOUTH);
            setContentPane(miolo);
            ligarBotoes();
            getRootPane().setDefaultButton(botaoGerar);
            escrever("Gerador de Arquivo TXT v" + VERSAO + " - by " + AUTOR, AZUL);
            carregarConfig();
            pack();
            setMinimumSize(new Dimension(720, 540));
            // nem menor que caber, nem maior que a tela do usuario
            Dimension tela = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
            int largura = Math.min(Math.max(getWidth(), 840), Math.max(840, tela.width - 80));
            int altura = Math.min(Math.max(getHeight(), 620), Math.max(620, tela.height - 80));
            setSize(new Dimension(largura, altura));
            setLocationRelativeTo(null);
        }

        // ------------------------------------------------------------------
        // faixa colorida do alto
        // ------------------------------------------------------------------

        private JComponent montarFaixa() {
            JPanel faixa = new JPanel(new GridBagLayout()) {
                private static final long serialVersionUID = 1L;

                @Override
                protected void paintComponent(java.awt.Graphics g) {
                    super.paintComponent(g);
                    java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                    g2.setPaint(new java.awt.GradientPaint(0, 0, AZUL_ESCURO,
                            getWidth(), getHeight(), AZUL));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.dispose();
                }
            };
            faixa.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

            GridBagConstraints g = new GridBagConstraints();
            g.anchor = GridBagConstraints.WEST;
            g.gridx = 0;
            g.gridy = 0;
            JLabel titulo = new JLabel("Gerador de Arquivo TXT");
            titulo.setForeground(Color.WHITE);
            titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 20f));
            faixa.add(titulo, g);

            g.gridy = 1;
            JLabel subtitulo = new JLabel("layout 6000/6100  -  no lugar das macros lDom, ISel"
                    + " e Verifica_Arquivo");
            subtitulo.setForeground(AZUL_CLARO);
            faixa.add(subtitulo, g);

            g.gridx = 1;
            g.gridy = 0;
            g.gridheight = 2;
            g.weightx = 1;
            g.anchor = GridBagConstraints.EAST;
            JPanel credito = new JPanel(new GridBagLayout());
            credito.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = 0;
            c.anchor = GridBagConstraints.EAST;
            JLabel porQuem = new JLabel("by " + AUTOR);
            porQuem.setForeground(Color.WHITE);
            porQuem.setFont(porQuem.getFont().deriveFont(Font.BOLD, 14f));
            credito.add(porQuem, c);
            c.gridy = 1;
            JLabel empresa = new JLabel(EMPRESA);
            empresa.setForeground(AZUL_CLARO);
            credito.add(empresa, c);
            c.gridy = 2;
            JLabel versao = new JLabel("versao " + VERSAO);
            versao.setForeground(AZUL_CLARO);
            credito.add(versao, c);
            faixa.add(credito, g);
            return faixa;
        }

        // ------------------------------------------------------------------
        // abas
        // ------------------------------------------------------------------

        private JComponent montarAbas() {
            javax.swing.JTabbedPane abas = new javax.swing.JTabbedPane();
            abas.setFont(abas.getFont().deriveFont(Font.BOLD));
            abas.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
            abas.addTab("Gerar arquivo", montarPainelGerar());
            abas.addTab("Como funciona", montarAjuda(textoComoFunciona()));
            abas.addTab("Se der erro", montarAjuda(textoSeDerErro()));
            abas.setToolTipTextAt(0, "A tela de trabalho: planilha, destino e o resultado.");
            abas.setToolTipTextAt(1, "Explicacao do programa e o que ele espera na planilha.");
            abas.setToolTipTextAt(2, "O que na planilha faz o programa parar, e como resolver.");
            return abas;
        }

        private JComponent montarAjuda(String html) {
            JEditorPane pagina = new JEditorPane();
            pagina.setContentType("text/html");
            pagina.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            pagina.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            pagina.setText(html);
            pagina.setEditable(false);
            pagina.setBackground(Color.WHITE);
            pagina.setCaretPosition(0);
            JScrollPane rolagem = new JScrollPane(pagina);
            rolagem.setBorder(BorderFactory.createEmptyBorder());
            rolagem.getVerticalScrollBar().setUnitIncrement(16);
            // sem isto o texto todo entra na conta do pack() e a janela sai da tela
            rolagem.setPreferredSize(new Dimension(680, 320));
            return rolagem;
        }

        private JComponent montarPainelGerar() {
            JPanel painel = new JPanel(new BorderLayout(0, 8));
            painel.setBackground(FUNDO);
            painel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            painel.add(montarEntrada(), BorderLayout.NORTH);
            painel.add(montarRegistro(), BorderLayout.CENTER);
            painel.add(montarEstado(), BorderLayout.SOUTH);
            return painel;
        }

        private JPanel montarEntrada() {
            JPanel painel = new JPanel(new GridBagLayout());
            painel.setBackground(Color.WHITE);
            painel.setBorder(BorderFactory.createCompoundBorder(
                    quadro("Planilha e destino"),
                    BorderFactory.createEmptyBorder(2, 6, 6, 8)));
            GridBagConstraints g = new GridBagConstraints();
            g.anchor = GridBagConstraints.WEST;
            g.insets = new Insets(3, 6, 3, 6);

            JButton escolherPlanilha = new JButton("Selecionar...");
            JButton escolherDestino = new JButton("Selecionar...");

            g.gridy = 0;
            g.gridx = 0;
            painel.add(negrito(new JLabel("Planilha:")), g);
            g.gridx = 1;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.weightx = 1;
            campoPlanilha.setToolTipText("Caminho do .xlsx ou .xlsm. O programa so le este arquivo.");
            painel.add(campoPlanilha, g);
            g.fill = GridBagConstraints.NONE;
            g.weightx = 0;
            g.gridx = 2;
            painel.add(escolherPlanilha, g);

            g.gridy = 1;
            g.gridx = 0;
            JLabel rotulo = new JLabel("Abas:");
            rotulo.setForeground(CINZA_TEXTO);
            painel.add(rotulo, g);
            g.gridx = 1;
            g.gridwidth = 2;
            rotuloAbas.setForeground(AZUL);
            painel.add(negrito(rotuloAbas), g);

            g.gridwidth = 1;
            g.gridy = 2;
            g.gridx = 0;
            g.insets = new Insets(10, 6, 3, 6);
            painel.add(negrito(new JLabel("Salvar txt em:")), g);
            g.gridx = 1;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.weightx = 1;
            campoDestino.setToolTipText("Deixe em branco para usar a pasta e o nome da aba Principal.");
            painel.add(campoDestino, g);
            g.fill = GridBagConstraints.NONE;
            g.weightx = 0;
            g.gridx = 2;
            painel.add(escolherDestino, g);

            g.gridy = 3;
            g.gridx = 1;
            g.gridwidth = 2;
            g.insets = new Insets(0, 6, 4, 6);
            JLabel dica = new JLabel("em branco = usa a pasta (B9) e o nome (B10) da aba Principal");
            dica.setForeground(CINZA_TEXTO);
            painel.add(dica, g);

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
            return painel;
        }

        private JComponent montarRegistro() {
            JPanel painel = new JPanel(new BorderLayout(0, 6));
            painel.setBackground(FUNDO);

            JPanel linhaBotoes = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
            linhaBotoes.setBackground(FUNDO);
            botaoGerar.setMnemonic('G');
            botaoGerar.setFont(botaoGerar.getFont().deriveFont(Font.BOLD));
            botaoGerar.setBackground(AZUL);
            botaoGerar.setForeground(Color.WHITE);
            botaoGerar.setOpaque(true);
            botaoGerar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(AZUL_ESCURO),
                    BorderFactory.createEmptyBorder(5, 14, 5, 14)));
            botaoTxt.setMnemonic('T');
            botaoPasta.setMnemonic('P');
            botaoRecarregar.setMnemonic('R');
            botaoTxt.setEnabled(false);
            botaoPasta.setEnabled(false);
            botaoGerar.setToolTipText("Le a planilha e grava o txt. Atalho: Enter");
            botaoTxt.setToolTipText("Abre o txt gerado no programa padrao do Windows.");
            botaoPasta.setToolTipText("Abre a pasta onde o txt foi gravado.");
            botaoRecarregar.setToolTipText("Le de novo o config.properties, depois de voce edita-lo.");
            linhaBotoes.add(botaoGerar);
            linhaBotoes.add(botaoTxt);
            linhaBotoes.add(botaoPasta);
            linhaBotoes.add(botaoRecarregar);
            painel.add(linhaBotoes, BorderLayout.NORTH);

            registro.setEditorKit(new KitQueQuebra());
            registro.setEditable(false);
            registro.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            registro.setMargin(new Insets(6, 8, 6, 8));
            JScrollPane rolagem = new JScrollPane(registro,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            rolagem.setBorder(quadro("Registro"));
            rolagem.setPreferredSize(new Dimension(660, 220));
            painel.add(rolagem, BorderLayout.CENTER);
            return painel;
        }

        private JComponent montarEstado() {
            JPanel painel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 2));
            painel.setBackground(FUNDO);
            barra.setIndeterminate(true);
            barra.setVisible(false);
            barra.setPreferredSize(new Dimension(130, 14));
            painel.add(barra);
            rotuloEstado.setOpaque(true);
            rotuloEstado.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
            painel.add(negrito(rotuloEstado));
            estado("pronto", AZUL, AZUL_CLARO);
            return painel;
        }

        /** Moldura com titulo colorido, usada nos dois blocos da tela. */
        private static javax.swing.border.Border quadro(String titulo) {
            javax.swing.border.TitledBorder borda = BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(AZUL_CLARO), titulo);
            borda.setTitleColor(AZUL);
            borda.setTitleFont(borda.getTitleFont().deriveFont(Font.BOLD));
            return borda;
        }

        private JPanel montarRodape() {
            JPanel painel = new JPanel(new GridBagLayout());
            painel.setBackground(AZUL_ESCURO);
            painel.setBorder(BorderFactory.createEmptyBorder(5, 16, 5, 16));
            GridBagConstraints g = new GridBagConstraints();
            g.gridx = 0;
            g.gridy = 0;
            g.anchor = GridBagConstraints.WEST;
            JLabel credito = new JLabel("by " + AUTOR + "  -  " + EMPRESA);
            credito.setForeground(Color.WHITE);
            painel.add(negrito(credito), g);

            g.gridx = 1;
            g.weightx = 1;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.anchor = GridBagConstraints.EAST;
            String pasta = arquivoConfig.getAbsoluteFile().getParent();
            JLabel caminho = new JLabel("config e log em: " + pasta, JLabel.RIGHT);
            caminho.setForeground(AZUL_CLARO);
            caminho.setToolTipText(pasta);
            // um caminho comprido nao pode esticar a janela inteira
            caminho.setMinimumSize(new Dimension(1, caminho.getPreferredSize().height));
            caminho.setPreferredSize(new Dimension(1, caminho.getPreferredSize().height));
            painel.add(caminho, g);
            return painel;
        }

        private static JLabel negrito(JLabel rotulo) {
            rotulo.setFont(rotulo.getFont().deriveFont(Font.BOLD));
            return rotulo;
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
                    abrir(ultimoArquivo == null ? null : ultimoArquivo.getAbsoluteFile().getParentFile());
                }
            });
            botaoTxt.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    abrir(ultimoArquivo);
                }
            });
        }

        // ------------------------------------------------------------------
        // acoes
        // ------------------------------------------------------------------

        private void carregarConfig() {
            try {
                cfg = Config.carregar(arquivoConfig);
                campoPlanilha.setText(cfg.planilhaCaminho);
                campoDestino.setText(cfg.saidaDestino);
                escrever("config lido de " + cfg.arquivo.getAbsolutePath(), null);
                mostrarAbas();
                botaoGerar.setEnabled(true);
                estado("pronto", AZUL, AZUL_CLARO);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "falha ao ler o config", e);
                escrever("ERRO: " + mensagem(e), VERMELHO);
                botaoGerar.setEnabled(false);
                estado("config com problema", VERMELHO, VERMELHO_FUNDO);
            }
        }

        private void mostrarAbas() {
            String caminho = campoPlanilha.getText().trim();
            if (vazio(caminho) || !new File(caminho).isFile()) {
                rotuloAbas.setForeground(VERMELHO);
                rotuloAbas.setText("planilha nao encontrada neste caminho");
                return;
            }
            List<String> avisos = new ArrayList<String>();
            try {
                LeitorPlanilha leitor = new LeitorPlanilha(new File(caminho), avisos);
                try {
                    rotuloAbas.setForeground(AZUL);
                    rotuloAbas.setText(juntar(leitor.nomesDasAbas(), ", "));
                } finally {
                    leitor.fechar();
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "falha ao listar as abas", e);
                rotuloAbas.setForeground(VERMELHO);
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
                seletor.setCurrentDirectory(new File(atual).getParentFile());
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
            barra.setVisible(true);
            estado("gerando...", AZUL, AZUL_CLARO);
            escrever("gerando...", null);
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
                    barra.setVisible(false);
                    try {
                        Resultado r = get();
                        for (int i = 0; i < r.avisos.size(); i++) {
                            escrever("AVISO: " + r.avisos.get(i), LARANJA);
                        }
                        escrever("pronto: " + r.registros + " registros, " + r.bytes + " bytes", VERDE);
                        escrever("arquivo: " + r.arquivo.getAbsolutePath(), null);
                        campoDestino.setText(r.arquivo.getAbsolutePath());
                        ultimoArquivo = r.arquivo;
                        botaoPasta.setEnabled(true);
                        botaoTxt.setEnabled(true);
                        String resumo = r.registros + " registros, " + r.bytes + " bytes, "
                                + r.avisos.size() + (r.avisos.size() == 1 ? " aviso" : " avisos");
                        if (r.avisos.isEmpty()) {
                            estado(resumo, VERDE, VERDE_FUNDO);
                        } else {
                            estado(resumo, LARANJA, LARANJA_FUNDO);
                        }
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, "falha ao gerar", e);
                        escrever("ERRO: " + mensagem(e), VERMELHO);
                        estado("falhou - veja o Registro e a aba Se der erro", VERMELHO,
                                VERMELHO_FUNDO);
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

        private void abrir(File alvo) {
            if (alvo == null) {
                return;
            }
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(alvo);
                } else {
                    escrever("ERRO: este Windows nao deixa o programa abrir arquivos.", VERMELHO);
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "falha ao abrir " + alvo, e);
                escrever("ERRO: nao consegui abrir " + alvo.getAbsolutePath(), VERMELHO);
            }
        }

        // ------------------------------------------------------------------
        // registro e estado na tela
        // ------------------------------------------------------------------

        private void estado(String texto, Color cor, Color fundo) {
            rotuloEstado.setText(texto);
            rotuloEstado.setForeground(cor);
            rotuloEstado.setBackground(fundo);
        }

        /** Escreve uma linha no registro. Cor nula = cor normal do texto. */
        private void escrever(String linha, Color cor) {
            javax.swing.text.SimpleAttributeSet estilo = new javax.swing.text.SimpleAttributeSet();
            if (cor != null) {
                javax.swing.text.StyleConstants.setForeground(estilo, cor);
                javax.swing.text.StyleConstants.setBold(estilo, true);
            }
            javax.swing.text.Document documento = registro.getDocument();
            try {
                documento.insertString(documento.getLength(), linha + "\n", estilo);
            } catch (javax.swing.text.BadLocationException e) {
                LOG.log(Level.FINE, "falha ao escrever no registro da tela", e);
            }
            registro.setCaretPosition(documento.getLength());
        }

        // ------------------------------------------------------------------
        // texto das abas de ajuda
        // ------------------------------------------------------------------

        private static String textoComoFunciona() {
            return ""
                + "<html><body style='font-family:sans-serif; font-size:12px; margin:4px 10px 10px "
                + "10px'> "
                + "<h2 style='color:#102E54; margin-bottom:2px'>Gerador de Arquivo TXT</h2> "
                + "<div style='color:#5F6976'>O que este programa faz, e o que ele espera encontrar na "
                + "planilha.</div> "
                + "<hr> "
                + "<h3 style='color:#1D5B9A'>1. Para que ele serve</h3> "
                + "<p>Ele faz o que as macros <b>lDom</b>, <b>ISel</b> e <b>Verifica_Arquivo</b> faziam: "
                + "l&ecirc; a planilha e grava um arquivo de texto no layout <b>6000/6100</b>, pronto "
                + "para "
                + "ser importado no sistema.</p> "
                + "<p>A diferen&ccedil;a &eacute; que a planilha agora pode ficar <b>limpa, sem macro "
                + "nenhuma</b> &mdash; e "
                + "assim a empresa pode desativar macros &agrave; vontade, que o trabalho continua "
                + "saindo.</p> "
                + "<p style='background:#E8F4EC; padding:6px'><b>O programa nunca escreve na "
                + "planilha.</b> "
                + "Ele s&oacute; l&ecirc;. Pode rodar com a planilha aberta que nada nela muda.</p> "
                + "<h3 style='color:#1D5B9A'>2. O que ele espera na aba Principal</h3> "
                + "<table cellpadding='4' cellspacing='0'> "
                + "<tr><td><b>B6</b></td><td>obrigat&oacute;ria &mdash; se estiver vazia, o programa "
                + "recusa gerar</td></tr> "
                + "<tr><td><b>B7</b></td><td>obrigat&oacute;ria &mdash; mesma coisa</td></tr> "
                + "<tr><td><b>B9</b></td><td>pasta onde salvar o txt</td></tr> "
                + "<tr><td><b>B10</b></td><td>nome do arquivo, <b>sem</b> o <code>.txt</code></td></tr> "
                + "</table> "
                + "<p>B9 e B10 s&oacute; s&atilde;o usadas quando o campo <b>Salvar txt em</b> "
                + "est&aacute; em branco. Se voc&ecirc; "
                + "escolher o destino na tela, ele manda.</p> "
                + "<h3 style='color:#1D5B9A'>3. O que ele espera na aba Base</h3> "
                + "<p>Uma linha por registro, das colunas <b>A at&eacute; I</b>, come&ccedil;ando na "
                + "<b>linha 2</b> &mdash; a "
                + "linha 1 &eacute; o cabe&ccedil;alho e &eacute; ignorada. Cada linha preenchida "
                + "vir&aacute; a ser um par de "
                + "linhas no txt.</p> "
                + "<p>Linha totalmente vazia no meio da Base &eacute; <b>pulada</b>, e a varredura "
                + "continua at&eacute; o "
                + "fim. A macro antiga parava na primeira vazia e cortava o arquivo pela metade; "
                + "quem quiser o jeito antigo p&otilde;e <code>base.pararNaLinhaVazia=true</code> no "
                + "config.</p> "
                + "<h3 style='color:#1D5B9A'>4. O que sai no arquivo</h3> "
                + "<p>Para cada linha da Base, duas linhas no txt:</p> "
                + "<pre style='background:#F2F5F9; padding:6px; font-size:11px'>6000|X|||| "
                + "6100|001|JO&Atilde;O ATACAD&Atilde;O LTDA|1234,5|31/01/2026|3|acordo|||FIM|</pre> "
                + "<p>O arquivo come&ccedil;a com uma <b>linha em branco</b>, &eacute; gravado em "
                + "<b>windows-1252</b> e "
                + "quebra linha com <b>CRLF</b> &mdash; exatamente como o <code>Print #</code> do VBA "
                + "fazia. Mudar "
                + "qualquer uma dessas tr&ecirc;s coisas &eacute; mexer no config, n&atilde;o no "
                + "programa.</p> "
                + "<h3 style='color:#1D5B9A'>5. Como usar no dia a dia</h3> "
                + "<ol> "
                + "<li>Confira o caminho da <b>Planilha</b>. O campo <b>Abas</b> mostra os nomes que "
                + "existem "
                + "    de verdade no arquivo &mdash; serve de confer&ecirc;ncia.</li> "
                + "<li>Deixe <b>Salvar txt em</b> em branco para usar B9 e B10, ou escolha o "
                + "destino.</li> "
                + "<li>Clique <b>Gerar arquivo</b> (ou aperte Enter).</li> "
                + "<li>Leia o <b>Registro</b>. Laranja &eacute; aviso, vermelho &eacute; erro, verde "
                + "&eacute; o resultado.</li> "
                + "<li><b>Abrir txt</b> abre o arquivo gerado; <b>Abrir pasta</b> abre a pasta "
                + "dele.</li> "
                + "</ol> "
                + "<h3 style='color:#1D5B9A'>6. Onde ficam as configura&ccedil;&otilde;es</h3> "
                + "<p>Na mesma pasta do programa ficam o <b>config.properties</b> &mdash; caminhos, "
                + "colunas, "
                + "prefixos, codifica&ccedil;&atilde;o &mdash; e o <b>gerador_arquivo.log</b>, que "
                + "guarda toda gera&ccedil;&atilde;o e "
                + "todo erro, com data e hora. O caminho exato est&aacute; no p&eacute; desta "
                + "janela.</p> "
                + "<p>Depois de editar o config, clique <b>Recarregar config</b>: n&atilde;o precisa "
                + "fechar o "
                + "programa.</p> "
                + "<h3 style='color:#1D5B9A'>7. Sem janela, para o agendador</h3> "
                + "<p>O <code>gerar-agora.bat</code> gera o txt sem abrir nada, usando o config. "
                + "&Eacute; o que se "
                + "coloca no Agendador de Tarefas do Windows. Nesse caso deixe "
                + "<code>saida.sobrescrever=sempre</code>, sen&atilde;o a segunda execu&ccedil;&atilde;o "
                + "recusa gravar porque o "
                + "arquivo do dia anterior ainda est&aacute; l&aacute;.</p> "
                + "</body></html> ";
        }

        private static String textoSeDerErro() {
            return ""
                + "<html><body style='font-family:sans-serif; font-size:12px; margin:4px 10px 10px "
                + "10px'> "
                + "<h2 style='color:#102E54; margin-bottom:2px'>Se der erro</h2> "
                + "<div style='color:#5F6976'>O que na planilha faz o programa parar, e o que s&oacute; "
                + "muda o "
                + "resultado sem avisar alto.</div> "
                + "<hr> "
                + "<h3 style='color:#B01C1C'>Faz o programa PARAR sem gerar nada</h3> "
                + "<table cellpadding='5' cellspacing='0'> "
                + "<tr style='background:#F2F5F9'><td><b>O que est&aacute; errado</b></td><td><b>O que "
                + "fazer</b></td></tr> "
                + "<tr><td><b>Planilha n&atilde;o est&aacute; no caminho</b> do config &mdash; "
                + "algu&eacute;m moveu, renomeou "
                + "    ou a rede caiu</td><td>clique <b>Selecionar...</b> e aponte o arquivo, ou corrija "
                + "    <code>planilha.caminho</code></td></tr> "
                + "<tr style='background:#FAFBFD'><td><b>Arquivo &eacute; .xls antigo</b> (formato "
                + "bin&aacute;rio) ou "
                + "    est&aacute; corrompido</td><td>abra no Excel e salve como <b>.xlsx</b> ou "
                + "<b>.xlsm</b></td></tr> "
                + "<tr><td><b>Aba Principal ou Base n&atilde;o existe</b> com esse nome &mdash; "
                + "renomeada, com "
                + "    espa&ccedil;o sobrando, ou escrita diferente</td><td>o erro lista as abas "
                + "encontradas; "
                + "    ajuste o nome no config. Mai&uacute;scula/min&uacute;scula o programa resolve "
                + "sozinho e avisa</td></tr> "
                + "<tr style='background:#FAFBFD'><td><b>B6 ou B7 vazias</b> na aba Principal</td> "
                + "    <td>preencha as duas; s&atilde;o as c&eacute;lulas de controle que a macro "
                + "tamb&eacute;m exigia</td></tr> "
                + "<tr><td><b>Destino em branco na tela E B9/B10 vazias</b> na planilha</td> "
                + "    <td>preencha B9 e B10, ou escolha o destino no campo <b>Salvar txt "
                + "em</b></td></tr> "
                + "<tr style='background:#FAFBFD'><td><b>Aba Base sem nenhuma linha preenchida</b> a "
                + "partir "
                + "    da linha 2</td><td>confira se os dados n&atilde;o foram colados em outra "
                + "aba</td></tr> "
                + "<tr><td><b>Caractere que n&atilde;o existe em windows-1252</b> &mdash; emoji, "
                + "s&iacute;mbolo grego, "
                + "    caractere colado de site</td><td>o erro diz a linha; apague o caractere na "
                + "planilha. "
                + "    Acento comum, &ccedil;, ~ e &deg; podem ficar: esses existem na tabela</td></tr> "
                + "<tr style='background:#FAFBFD'><td><b>O txt j&aacute; existe</b> e o config "
                + "est&aacute; em "
                + "    <code>recusar</code></td><td>apague o txt antigo &mdash; &eacute; de "
                + "prop&oacute;sito, era o que a macro "
                + "    <b>Verifica_Arquivo</b> fazia. Para sobrescrever, mude para "
                + "<code>perguntar</code> "
                + "    ou <code>sempre</code></td></tr> "
                + "<tr><td><b>Pasta de destino n&atilde;o existe e n&atilde;o pode ser criada</b> "
                + "&mdash; unidade de rede "
                + "    fora do ar, sem permiss&atilde;o</td><td>confira se o I: ou a pasta da rede "
                + "est&aacute; "
                + "    acess&iacute;vel</td></tr> "
                + "</table> "
                + "<h3 style='color:#B56500'>N&atilde;o para, mas muda o arquivo &mdash; sempre com "
                + "aviso</h3> "
                + "<ul> "
                + "<li><b>F&oacute;rmula sem valor calculado.</b> O programa l&ecirc; o valor que "
                + "est&aacute; gravado na "
                + "    planilha, n&atilde;o recalcula nada. Planilha salva por outro programa pode vir "
                + "sem "
                + "    esse valor: abra no Excel, deixe calcular e salve. O campo sai vazio e o aviso "
                + "    aparece.</li> "
                + "<li><b>Barra vertical dentro do dado.</b> O <code>|</code> separa os campos, "
                + "ent&atilde;o um "
                + "    <code>|</code> digitado no meio do nome quebraria o layout. Ele &eacute; trocado "
                + "por "
                + "    espa&ccedil;o e o aviso diz em qual c&eacute;lula.</li> "
                + "<li><b>Coluna A vazia com dados no resto da linha.</b> A linha &eacute; gravada e o "
                + "aviso "
                + "    pede confer&ecirc;ncia &mdash; pode ser dado colado na linha errada.</li> "
                + "<li><b>Nome da aba com outra caixa</b> (PRINCIPAL x Principal). Funciona, mas o aviso "
                + "    fica aparecendo at&eacute; o config bater com o nome de verdade.</li> "
                + "</ul> "
                + "<h3 style='color:#00743E'>N&atilde;o avisa nada, e &eacute; onde mora o perigo</h3> "
                + "<p>Estas quatro coisas geram um arquivo <i>perfeito</i> &mdash; com o conte&uacute;do "
                + "errado. Vale "
                + "conferir na primeira vez:</p> "
                + "<ul> "
                + "<li><b>M&aacute;scara n&atilde;o vai para o txt, o valor vai.</b> C&eacute;lula que "
                + "mostra "
                + "    <b>1.234,50</b> tem valor 1234,5 e &eacute; <b>1234,5</b> que sai. Era o que o "
                + "VBA "
                + "    gravava. Se o sistema exige duas casas sempre, isso tem de ser tratado.</li> "
                + "<li><b>Data tem de ser data de verdade.</b> Se a data foi digitada como texto, sai "
                + "    exatamente como est&aacute; escrita &mdash; <b>31.01.26</b> continua "
                + "<b>31.01.26</b>. E se a "
                + "    c&eacute;lula tem data mas est&aacute; formatada como Geral, sai o n&uacute;mero "
                + "de s&eacute;rie do Excel "
                + "    (<b>46053</b>) em vez da data.</li> "
                + "<li><b>C&eacute;lula mesclada</b> guarda o valor s&oacute; na primeira c&eacute;lula; "
                + "as outras v&ecirc;m "
                + "    vazias, e &eacute; isso que vai para o arquivo.</li> "
                + "<li><b>Linha oculta ou escondida por filtro &eacute; lida igual.</b> O filtro "
                + "&eacute; enfeite de "
                + "    tela: para o programa, a linha est&aacute; l&aacute;.</li> "
                + "</ul> "
                + "<p style='background:#FFF6E5; padding:6px'><b>Espa&ccedil;o sobrando no fim do texto "
                + "tamb&eacute;m "
                + "vai para o arquivo</b>, porque o programa grava o que est&aacute; na c&eacute;lula, "
                + "sem aparar.</p> "
                + "<h3 style='color:#1D5B9A'>Quando nada disso explicar</h3> "
                + "<p>O <b>gerador_arquivo.log</b>, na pasta do programa, guarda o erro completo com "
                + "data e "
                + "hora. &Eacute; o arquivo que resolve a d&uacute;vida &mdash; mande ele junto ao pedir "
                + "ajuda.</p> "
                + "</body></html> ";
        }

        // ------------------------------------------------------------------
        // quebra de palavra comprida no registro
        // ------------------------------------------------------------------
        //
        // O JTextPane so quebra a linha entre palavras, e um caminho de arquivo
        // nao tem espaco nenhum: o caminho passava da borda e sumia. Trocar o
        // EditorKit e o jeito padrao de resolver - a LabelView passa a aceitar
        // largura minima zero, o que autoriza quebrar no meio da palavra.

        private static final class KitQueQuebra extends javax.swing.text.StyledEditorKit {
            private static final long serialVersionUID = 1L;

            private final transient javax.swing.text.ViewFactory fabrica = new FabricaQueQuebra();

            @Override
            public javax.swing.text.ViewFactory getViewFactory() {
                return fabrica;
            }
        }

        private static final class FabricaQueQuebra implements javax.swing.text.ViewFactory {
            public javax.swing.text.View create(javax.swing.text.Element elemento) {
                String tipo = elemento.getName();
                if (tipo != null) {
                    if (javax.swing.text.AbstractDocument.ContentElementName.equals(tipo)) {
                        return new RotuloQueQuebra(elemento);
                    }
                    if (javax.swing.text.AbstractDocument.ParagraphElementName.equals(tipo)) {
                        return new javax.swing.text.ParagraphView(elemento);
                    }
                    if (javax.swing.text.AbstractDocument.SectionElementName.equals(tipo)) {
                        return new javax.swing.text.BoxView(elemento,
                                javax.swing.text.View.Y_AXIS);
                    }
                    if (javax.swing.text.StyleConstants.ComponentElementName.equals(tipo)) {
                        return new javax.swing.text.ComponentView(elemento);
                    }
                    if (javax.swing.text.StyleConstants.IconElementName.equals(tipo)) {
                        return new javax.swing.text.IconView(elemento);
                    }
                }
                return new javax.swing.text.LabelView(elemento);
            }
        }

        private static final class RotuloQueQuebra extends javax.swing.text.LabelView {
            RotuloQueQuebra(javax.swing.text.Element elemento) {
                super(elemento);
            }

            @Override
            public float getMinimumSpan(int eixo) {
                // zero na horizontal = pode quebrar onde precisar
                return eixo == javax.swing.text.View.X_AXIS ? 0f : super.getMinimumSpan(eixo);
            }
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
