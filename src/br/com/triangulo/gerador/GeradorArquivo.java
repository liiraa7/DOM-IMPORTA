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
 * ADAPTED DOM IMPORT - gerador do arquivo TXT no layout 6000/6100.
 *
 * Substitui as macros VBA lDom, ISel e Verifica_Arquivo: le a planilha
 * (.xlsx/.xlsm) e escreve o txt. NUNCA escreve na planilha.
 *
 * Regras da casa (ver CLAUDE.md): classe unica, sem dependencia externa,
 * compilavel com "javac --release 8".
 */
public final class GeradorArquivo {

    static final String VERSAO = "3.10.0";
    static final String NOME_PROGRAMA = "ADAPTED DOM IMPORT";
    static final String AUTOR = "Ronald Lira";
    static final String EMPRESA = "Triangulo Contabilidade";
    static final String NOME_CONFIG = "config.properties";
    static final String NOME_MODELO = "config-modelo.properties";
    static final String NOME_LOG = "gerador_arquivo.log";

    /** Mostrada em todo erro: o usuario tem de saber a quem recorrer. */
    static final String CONTATO = "Se nao souber resolver, veja a aba \"Se der erro\""
            + " ou fale com " + AUTOR + ", que fez o programa."
            + " Leve junto o arquivo " + NOME_LOG + " da pasta do programa.";

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
            arquivoConfig = configDoUsuario();
        }
        prepararLog(arquivoConfig.getAbsoluteFile().getParentFile());
        if (console) {
            System.exit(executarConsole(arquivoConfig));
        } else {
            abrirJanela(arquivoConfig);
        }
    }

    private static void imprimirAjuda() {
        System.out.println(NOME_PROGRAMA + " v" + VERSAO + " - " + AUTOR);
        System.out.println("  (sem argumento)     abre a janela");
        System.out.println("  --console           gera o txt sem janela, usando o config.properties");
        System.out.println("  --config <arquivo>  usa outro config.properties");
    }

    private static int executarConsole(File arquivoConfig) {
        try {
            Config cfg = Config.carregar(arquivoConfig);
            System.out.println(NOME_PROGRAMA + " v" + VERSAO + " - " + AUTOR + " (console)");
            System.out.println("config   = " + cfg.arquivo.getAbsolutePath());
            System.out.println("planilha = " + cfg.planilhaCaminho);
            // caminho vazio = deixa o programa montar o nome dos campos do config
            Resultado r = gerar(cfg, "", new Confirmacao() {
                public boolean gerarDeNovo(File arquivo, String quando) {
                    // No agendador nao ha ninguem para responder: avisa e segue.
                    System.out.println("AVISO: este arquivo ja havia sido gerado em " + quando + ".");
                    return true;
                }

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
            System.err.println(CONTATO);
            return 1;
        }
    }

    // ------------------------------------------------------------------
    // logo do escritorio
    // ------------------------------------------------------------------

    private static java.awt.image.BufferedImage logoOriginal;
    private static boolean logoProcurado;

    /**
     * Logo que vem dentro do jar, ao lado da classe. Devolve null se o arquivo
     * nao estiver la - o programa roda sem logo, so fica com o icone padrao.
     */
    static java.awt.image.BufferedImage logo() {
        if (!logoProcurado) {
            logoProcurado = true;
            InputStream in = GeradorArquivo.class.getResourceAsStream("logo.png");
            if (in != null) {
                try {
                    logoOriginal = javax.imageio.ImageIO.read(in);
                } catch (IOException e) {
                    LOG.log(Level.FINE, "nao consegui ler o logo.png", e);
                } finally {
                    try {
                        in.close();
                    } catch (IOException e) {
                        LOG.log(Level.FINE, "falha ao fechar o logo.png", e);
                    }
                }
            }
        }
        return logoOriginal;
    }

    /** O logo redesenhado no tamanho pedido, com as bordas suavizadas. */
    static java.awt.Image logoEm(int lado) {
        java.awt.image.BufferedImage base = logo();
        if (base == null) {
            return null;
        }
        java.awt.image.BufferedImage destino = new java.awt.image.BufferedImage(lado, lado,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = destino.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(base, 0, 0, lado, lado, null);
        g.dispose();
        return destino;
    }

    /**
     * Varios tamanhos do mesmo logo: o Windows escolhe um para a barra de
     * titulo, outro para a barra de tarefas e outro para o Alt+Tab.
     */
    static List<java.awt.Image> icones() {
        List<java.awt.Image> lista = new ArrayList<java.awt.Image>();
        int[] tamanhos = {16, 20, 24, 32, 48, 64, 128, 256};
        for (int i = 0; i < tamanhos.length; i++) {
            java.awt.Image imagem = logoEm(tamanhos[i]);
            if (imagem != null) {
                lista.add(imagem);
            }
        }
        return lista;
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

    /**
     * Onde ficam config e log. Normalmente e a pasta do programa; se ela nao
     * aceitar escrita - instalado em Program Files, por exemplo - vai para a
     * pasta do usuario, levando junto uma copia do config que veio instalado.
     */
    static File configDoUsuario() {
        File pastaPrograma = pastaBase();
        File naPasta = new File(pastaPrograma, NOME_CONFIG);
        File escolhido = naPasta;
        if (!podeEscrever(pastaPrograma)) {
            File pastaUsuario = pastaDoUsuario();
            File config = new File(pastaUsuario, NOME_CONFIG);
            if (!config.isFile() && naPasta.isFile()
                    && (pastaUsuario.isDirectory() || pastaUsuario.mkdirs())) {
                copiar(naPasta, config);
            }
            escolhido = config;
        }
        // primeira vez nesta maquina: o config nasce do modelo que veio junto
        if (!escolhido.isFile()) {
            File modelo = new File(pastaPrograma, NOME_MODELO);
            if (modelo.isFile()) {
                File pai = escolhido.getAbsoluteFile().getParentFile();
                if (pai == null || pai.isDirectory() || pai.mkdirs()) {
                    copiar(modelo, escolhido);
                }
            }
        }
        return escolhido;
    }

    private static void copiar(File origem, File destino) {
        try {
            Files.copy(origem.toPath(), destino.toPath());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "nao consegui criar " + destino.getAbsolutePath(), e);
        }
    }

    private static File pastaDoUsuario() {
        String appdata = System.getenv("APPDATA");
        if (!vazio(appdata)) {
            return new File(appdata, NOME_PROGRAMA);
        }
        return new File(System.getProperty("user.home", "."), "." + NOME_PROGRAMA.replace(' ', '-'));
    }

    /** canWrite() mente em algumas pastas do Windows; escrever de verdade, nao. */
    private static boolean podeEscrever(File pasta) {
        File teste = new File(pasta, ".teste-de-escrita.tmp");
        try {
            if (teste.createNewFile()) {
                return teste.delete();
            }
            return teste.isFile() && teste.delete();
        } catch (IOException e) {
            LOG.log(Level.FINE, "pasta do programa nao aceita escrita: " + pasta, e);
            return false;
        } catch (SecurityException e) {
            LOG.log(Level.FINE, "pasta do programa protegida: " + pasta, e);
            return false;
        }
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
        boolean usarAbaPrincipal;
        String empresa;
        String tipo;
        String competencia;
        String pasta;
        String nomePadrao;
        String ultimoArquivo;
        String ultimaGeracao;
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
            cfg.usarAbaPrincipal = logico(p, "controle.usarAbaPrincipal", false);
            cfg.empresa = texto(p, "saida.empresa", "");
            cfg.tipo = texto(p, "saida.tipo", "");
            cfg.competencia = texto(p, "saida.competencia", "");
            cfg.pasta = texto(p, "saida.pasta", "");
            cfg.nomePadrao = texto(p, "saida.nomePadrao", "{empresa}_{tipo}_{competencia}");
            cfg.ultimoArquivo = texto(p, "saida.ultimoArquivo", "");
            cfg.ultimaGeracao = texto(p, "saida.ultimaGeracao", "");
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
            cfg.migrarDestinoAntigo();
            cfg.validar();
            return cfg;
        }

        /**
         * Config da versao antiga guardava o caminho inteiro em saida.destino.
         * Aproveita o que da: a pasta, e - se o nome tiver a cara de
         * EMPRESA_TIPO_COMPETENCIA - os tres campos tambem.
         */
        private void migrarDestinoAntigo() {
            if (vazio(saidaDestino)) {
                return;
            }
            File antigo = new File(saidaDestino);
            if (vazio(pasta) && antigo.getParent() != null) {
                pasta = antigo.getParent();
            }
            if (!vazio(empresa) || !vazio(tipo) || !vazio(competencia)) {
                return;
            }
            String nome = antigo.getName();
            int ponto = nome.lastIndexOf('.');
            if (ponto > 0) {
                nome = nome.substring(0, ponto);
            }
            String[] partes = nome.split("_");
            if (partes.length == 3) {
                empresa = partes[0];
                tipo = partes[1];
                competencia = partes[2];
            }
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
         * Regrava no config.properties o que a pessoa digitou na janela,
         * preservando comentarios e o resto do arquivo - na proxima abertura os
         * campos ja vem preenchidos.
         *
         * Caminho sempre com barra normal: no .properties a barra invertida e
         * escape e engoliria o caractere seguinte.
         */
        void gravarCampos(String arquivoGerado) {
            String quando = java.time.LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            Map<String, String> valores = new LinkedHashMap<String, String>();
            valores.put("saida.empresa", empresa);
            valores.put("saida.tipo", tipo);
            valores.put("saida.competencia", competencia);
            valores.put("saida.pasta", pasta == null ? "" : pasta.replace('\\', '/'));
            valores.put("saida.ultimoArquivo",
                    arquivoGerado == null ? "" : arquivoGerado.replace('\\', '/'));
            valores.put("saida.ultimaGeracao", quando);
            gravar(valores);
            ultimoArquivo = arquivoGerado == null ? "" : arquivoGerado;
            ultimaGeracao = quando;
        }

        /** Este arquivo ja foi gerado antes? Devolve quando, ou null. */
        String geradoAntes(File destino) {
            if (vazio(ultimoArquivo) || vazio(ultimaGeracao)) {
                return null;
            }
            String antes = ultimoArquivo.replace('\\', '/');
            String agora = destino.getAbsolutePath().replace('\\', '/');
            return antes.equalsIgnoreCase(agora) ? ultimaGeracao : null;
        }

        private void gravar(Map<String, String> valores) {
            if (!arquivo.isFile()) {
                return;
            }
            try {
                String texto = lerTexto(arquivo);
                String fim = texto.indexOf("\r\n") >= 0 ? "\r\n" : "\n";
                String[] linhas = texto.split("\\r?\\n", -1);
                Set<String> achadas = new HashSet<String>();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < linhas.length; i++) {
                    String linha = linhas[i];
                    for (Map.Entry<String, String> e : valores.entrySet()) {
                        if (linha.trim().startsWith(e.getKey() + "=")) {
                            linha = e.getKey() + "=" + e.getValue();
                            achadas.add(e.getKey());
                        }
                    }
                    if (i > 0) {
                        sb.append(fim);
                    }
                    sb.append(linha);
                }
                for (Map.Entry<String, String> e : valores.entrySet()) {
                    if (!achadas.contains(e.getKey())) {
                        // sem isto, um config terminado em quebra de linha ganha
                        // uma linha em branco a cada chave nova
                        if (sb.length() > 0 && !terminaCom(sb, fim)) {
                            sb.append(fim);
                        }
                        sb.append(e.getKey()).append("=").append(e.getValue());
                    }
                }
                if (!terminaCom(sb, fim)) {
                    sb.append(fim);
                }
                Files.write(arquivo.toPath(), sb.toString().getBytes("ISO-8859-1"));
            } catch (IOException e) {
                LOG.log(Level.WARNING, "nao consegui regravar o config.properties", e);
            }
        }

        /**
         * Monta o nome do arquivo com o molde saida.nomePadrao. Campo vazio nao
         * deixa separador solto: "744__082025" viraria "744_082025".
         */
        String nomeMontado() {
            String nome = nomePadrao;
            nome = nome.replace("{empresa}", empresa == null ? "" : empresa.trim());
            nome = nome.replace("{tipo}", tipo == null ? "" : tipo.trim());
            nome = nome.replace("{competencia}", competencia == null ? "" : competencia.trim());
            while (nome.indexOf("__") >= 0) {
                nome = nome.replace("__", "_");
            }
            while (nome.startsWith("_")) {
                nome = nome.substring(1);
            }
            while (nome.endsWith("_")) {
                nome = nome.substring(0, nome.length() - 1);
            }
            return nome.trim();
        }

        private static boolean terminaCom(StringBuilder sb, String fim) {
            return sb.length() >= fim.length()
                    && sb.substring(sb.length() - fim.length()).equals(fim);
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

    /** Perguntas que so a tela sabe fazer; no modo console ninguem responde. */
    interface Confirmacao {
        /** O txt ja existe e saida.sobrescrever=perguntar. */
        boolean sobrescrever(File arquivo);

        /** Este mesmo arquivo ja foi gerado antes, em "quando". */
        boolean gerarDeNovo(File arquivo, String quando);
    }

    /** A pessoa desistiu na pergunta - nao e erro, nao merece caixa vermelha. */
    static final class Cancelado extends IOException {
        private static final long serialVersionUID = 1L;

        Cancelado(String mensagem) {
            super(mensagem);
        }
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
            // A aba Principal so existe para quem ficou no jeito antigo: hoje o
            // nome e o destino sao do programa, e a planilha pode ter so a Base.
            if (cfg.usarAbaPrincipal) {
                Aba principal = leitor.lerAba(cfg.abaPrincipal);
                conferirObrigatorias(cfg, principal);
                destino = destinoDaAbaPrincipal(cfg, principal, destinoEscolhido);
            } else {
                destino = destinoDosCampos(cfg, destinoEscolhido);
            }
            registros = lerRegistros(cfg, leitor.lerAba(cfg.abaBase), avisos);
        } finally {
            leitor.fechar();
        }
        if (registros.isEmpty()) {
            throw new IllegalStateException("A aba \"" + cfg.abaBase + "\" nao tem nenhuma linha preenchida"
                    + " a partir da linha " + cfg.linhaInicial + ".");
        }

        // Numa rotina mensal, o engano mais facil e esquecer de trocar a
        // competencia e gravar o mes novo por cima do anterior.
        String quandoAntes = cfg.geradoAntes(destino);
        if (quandoAntes != null) {
            if (confirmacao == null) {
                avisos.add("Este mesmo arquivo ja havia sido gerado em " + quandoAntes
                        + ". Confira se a competencia esta certa.");
            } else if (!confirmacao.gerarDeNovo(destino, quandoAntes)) {
                throw new Cancelado("Geracao cancelada - o arquivo " + destino.getName()
                        + " ja havia sido gerado em " + quandoAntes + ".");
            }
        }

        conferirExistente(cfg, destino, confirmacao);
        byte[] conteudo = montarConteudo(cfg, registros, avisos);

        File pasta = destino.getAbsoluteFile().getParentFile();
        if (pasta != null && !pasta.isDirectory() && !pasta.mkdirs()) {
            throw new IOException("Nao consegui criar a pasta " + pasta.getAbsolutePath());
        }
        Files.write(destino.toPath(), conteudo);
        cfg.gravarCampos(destino.getAbsolutePath());

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

    /** Jeito de hoje: pasta e campos vem do programa, nao da planilha. */
    private static File destinoDosCampos(Config cfg, String escolhido) {
        String caminho = escolhido == null ? "" : escolhido.trim();
        if (!vazio(caminho)) {
            return comExtensao(new File(caminho), cfg.extensao);
        }
        List<String> faltando = new ArrayList<String>();
        if (vazio(cfg.empresa)) {
            faltando.add("Empresa");
        }
        if (vazio(cfg.tipo)) {
            faltando.add("Tipo");
        }
        if (!faltando.isEmpty()) {
            throw new IllegalStateException("Preencha " + juntar(faltando, " e ")
                    + " - e disso que sai o nome do arquivo.");
        }
        String nome = cfg.nomeMontado();
        if (vazio(nome)) {
            throw new IllegalStateException("O nome do arquivo ficou vazio."
                    + " Confira o molde saida.nomePadrao no " + NOME_CONFIG + ".");
        }
        if (vazio(cfg.pasta)) {
            throw new IllegalStateException("Escolha a pasta onde salvar o arquivo " + nome
                    + cfg.extensao + ".");
        }
        return comExtensao(new File(cfg.pasta, nome), cfg.extensao);
    }

    /** Jeito antigo, so com controle.usarAbaPrincipal=true. */
    private static File destinoDaAbaPrincipal(Config cfg, Aba principal, String escolhido) {
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
        private final JTextField campoEmpresa = new JTextField(8);
        private final JTextField campoTipo = new JTextField(16);
        private final JTextField campoCompetencia = new JTextField(8);
        private final JTextField campoPasta = new JTextField(24);
        private final JLabel rotuloArquivo = new JLabel(" ");
        private final JLabel rotuloRepetido = new JLabel(" ");
        private final JLabel rotuloAbas = new JLabel(" ");
        private final JLabel rotuloAbasTitulo = new JLabel("Atencao:");
        private final JTextPane registro = new JTextPane();
        private final JProgressBar barra = new JProgressBar();
        private final JLabel rotuloEstado = new JLabel("pronto");
        private final JButton botaoGerar = new JButton("Gerar arquivo");
        private final JButton botaoTxt = new JButton("Abrir txt");
        private final JButton botaoPasta = new JButton("Abrir pasta");
        private final JButton botaoRecarregar = new JButton("Recarregar config");
        private transient Config cfg;
        private File ultimoArquivo;
        /**
         * Enquanto a tela esta sendo preenchida, o DocumentListener nao pode
         * copiar os campos de volta para o cfg: os que ainda nao foram
         * preenchidos estao vazios e apagariam o valor lido do config.
         */
        private boolean preenchendo;

        Janela(File arquivoConfig) {
            super(NOME_PROGRAMA + " v" + VERSAO + " - by " + AUTOR);
            this.arquivoConfig = arquivoConfig;
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            List<java.awt.Image> icones = icones();
            if (!icones.isEmpty()) {
                setIconImages(icones);
            }
            JPanel miolo = new JPanel(new BorderLayout());
            miolo.setBackground(FUNDO);
            miolo.add(montarFaixa(), BorderLayout.NORTH);
            miolo.add(montarAbas(), BorderLayout.CENTER);
            miolo.add(montarRodape(), BorderLayout.SOUTH);
            setContentPane(miolo);
            ligarBotoes();
            getRootPane().setDefaultButton(botaoGerar);
            escrever(NOME_PROGRAMA + " v" + VERSAO + " - by " + AUTOR, AZUL);
            carregarConfig();
            pack();
            setMinimumSize(new Dimension(720, 540));
            // nem menor que caber, nem maior que a tela do usuario
            Dimension tela = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
            int largura = Math.min(Math.max(getWidth(), 860), Math.min(1020, Math.max(860,
                    tela.width - 80)));
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

            java.awt.Image marca = logoEm(46);
            if (marca != null) {
                g.gridx = 0;
                g.gridy = 0;
                g.gridheight = 2;
                g.insets = new Insets(0, 0, 0, 12);
                faixa.add(new JLabel(new javax.swing.ImageIcon(marca)), g);
                g.gridheight = 1;
                g.insets = new Insets(0, 0, 0, 0);
            }

            g.gridx = 1;
            g.gridy = 0;
            JLabel titulo = new JLabel(NOME_PROGRAMA);
            titulo.setForeground(Color.WHITE);
            titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 20f));
            faixa.add(titulo, g);

            g.gridy = 1;
            JLabel subtitulo = new JLabel("Gerador de arquivos de texto a partir de planilhas do Excel");
            subtitulo.setForeground(AZUL_CLARO);
            faixa.add(subtitulo, g);

            g.gridx = 2;
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
            abas.addTab("Como usar", montarAjuda(textoComoFunciona()));
            abas.addTab("Se der erro", montarAjuda(textoSeDerErro()));
            abas.setToolTipTextAt(0, "A tela de trabalho: planilha, destino e o resultado.");
            abas.setToolTipTextAt(1, "O passo a passo de uso, do comeco ao arquivo pronto.");
            abas.setToolTipTextAt(2, "O que costuma dar errado, o que fazer, e a quem recorrer.");
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
                    quadro("Planilha e arquivo a gerar"),
                    BorderFactory.createEmptyBorder(2, 6, 6, 8)));
            GridBagConstraints g = new GridBagConstraints();
            g.anchor = GridBagConstraints.WEST;
            g.insets = new Insets(3, 6, 3, 6);

            JButton escolherPlanilha = new JButton("Selecionar...");
            JButton escolherPasta = new JButton("Selecionar...");

            // --- linha 1: a planilha
            g.gridy = 0;
            g.gridx = 0;
            painel.add(negrito(new JLabel("Planilha:")), g);
            g.gridx = 1;
            g.gridwidth = 4;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.weightx = 1;
            campoPlanilha.setToolTipText("Caminho do .xlsx ou .xlsm. O programa so le este arquivo.");
            painel.add(campoPlanilha, g);
            g.gridwidth = 1;
            g.fill = GridBagConstraints.NONE;
            g.weightx = 0;
            g.gridx = 5;
            painel.add(escolherPlanilha, g);

            // --- linha 2: as abas que existem de verdade
            g.gridy = 1;
            g.gridx = 0;
            rotuloAbasTitulo.setForeground(VERMELHO);
            painel.add(negrito(rotuloAbasTitulo), g);
            g.gridx = 1;
            g.gridwidth = 5;
            painel.add(negrito(rotuloAbas), g);
            g.gridwidth = 1;
            rotuloAbasTitulo.setVisible(false);
            rotuloAbas.setVisible(false);

            // --- linha 3: os tres campos que montam o nome
            g.gridy = 2;
            g.gridx = 0;
            g.insets = new Insets(12, 6, 3, 6);
            painel.add(negrito(new JLabel("Empresa*:")), g);
            g.gridx = 1;
            campoEmpresa.setToolTipText("Codigo da empresa - o 744 do nome do arquivo.");
            painel.add(campoEmpresa, g);
            g.gridx = 2;
            painel.add(negrito(new JLabel("Tipo*:")), g);
            g.gridx = 3;
            campoTipo.setToolTipText("PARCELAMENTOS, por exemplo.");
            painel.add(campoTipo, g);
            g.gridx = 4;
            JLabel rotuloComp = new JLabel("Competencia:");
            painel.add(negrito(rotuloComp), g);
            g.gridx = 5;
            campoCompetencia.setToolTipText("082025, por exemplo.");
            painel.add(campoCompetencia, g);

            // --- linha 4: a pasta
            g.insets = new Insets(3, 6, 3, 6);
            g.gridy = 3;
            g.gridx = 0;
            painel.add(negrito(new JLabel("Pasta:")), g);
            g.gridx = 1;
            g.gridwidth = 4;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.weightx = 1;
            campoPasta.setToolTipText("Pasta onde o txt vai ser gravado.");
            painel.add(campoPasta, g);
            g.gridwidth = 1;
            g.fill = GridBagConstraints.NONE;
            g.weightx = 0;
            g.gridx = 5;
            painel.add(escolherPasta, g);

            // --- linha 5: a previa do que vai ser gravado
            g.gridy = 4;
            g.gridx = 0;
            g.gridwidth = 6;
            g.fill = GridBagConstraints.HORIZONTAL;
            g.insets = new Insets(8, 6, 2, 6);
            rotuloArquivo.setForeground(AZUL);
            // caminho comprido nao pode esticar a janela: o texto e encurtado no
            // meio e o caminho inteiro fica na dica do mouse
            rotuloArquivo.setMinimumSize(new Dimension(1, rotuloArquivo.getPreferredSize().height));
            rotuloArquivo.setPreferredSize(new Dimension(1, rotuloArquivo.getPreferredSize().height));
            painel.add(negrito(rotuloArquivo), g);
            g.fill = GridBagConstraints.NONE;

            g.gridy = 5;
            g.insets = new Insets(0, 6, 2, 6);
            rotuloRepetido.setForeground(LARANJA);
            rotuloRepetido.setVisible(false);
            painel.add(negrito(rotuloRepetido), g);

            g.gridy = 6;
            g.insets = new Insets(0, 6, 4, 6);
            JLabel dica = new JLabel("* obrigatorios. O nome do arquivo sai destes tres campos.");
            dica.setForeground(CINZA_TEXTO);
            painel.add(dica, g);

            escolherPlanilha.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    escolherPlanilha();
                }
            });
            escolherPasta.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    escolherPasta();
                }
            });
            javax.swing.event.DocumentListener aoDigitar = new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) {
                    atualizarPrevia();
                }

                public void removeUpdate(javax.swing.event.DocumentEvent e) {
                    atualizarPrevia();
                }

                public void changedUpdate(javax.swing.event.DocumentEvent e) {
                    atualizarPrevia();
                }
            };
            campoEmpresa.getDocument().addDocumentListener(aoDigitar);
            campoTipo.getDocument().addDocumentListener(aoDigitar);
            campoCompetencia.getDocument().addDocumentListener(aoDigitar);
            campoPasta.getDocument().addDocumentListener(aoDigitar);
            return painel;
        }

        /** Mostra, em tempo real, o arquivo exato que o Gerar vai escrever. */
        private void atualizarPrevia() {
            if (cfg == null || preenchendo) {
                rotuloArquivo.setText(" ");
                return;
            }
            copiarCampos();
            if (vazio(cfg.empresa) || vazio(cfg.tipo)) {
                rotuloArquivo.setForeground(LARANJA);
                rotuloArquivo.setText("Preencha Empresa e Tipo - e deles que sai o nome do arquivo.");
                return;
            }
            String nome = cfg.nomeMontado() + cfg.extensao;
            if (vazio(cfg.pasta)) {
                rotuloArquivo.setForeground(LARANJA);
                rotuloArquivo.setText("Escolha a pasta. O arquivo vai se chamar " + nome);
                return;
            }
            rotuloArquivo.setForeground(AZUL);
            File destino = new File(cfg.pasta, nome);
            String caminho = destino.getPath();
            rotuloArquivo.setText("Vai gravar: " + encurtar(caminho, 80));
            rotuloArquivo.setToolTipText(caminho);

            // numa rotina mensal, o engano facil e esquecer de trocar a competencia
            String quando = cfg.geradoAntes(destino.getAbsoluteFile());
            if (quando == null) {
                rotuloRepetido.setVisible(false);
            } else {
                rotuloRepetido.setVisible(true);
                rotuloRepetido.setText("Atencao: este mesmo arquivo ja foi gerado em " + quando
                        + ". A competencia esta certa?");
            }
        }

        /** Corta o meio do caminho, que o fim - o nome do arquivo - e o que importa. */
        private static String encurtar(String caminho, int maximo) {
            if (caminho.length() <= maximo) {
                return caminho;
            }
            int inicio = Math.max(3, maximo / 4);
            int fim = maximo - inicio - 3;
            return caminho.substring(0, inicio) + "..."
                    + caminho.substring(caminho.length() - fim);
        }

        private void copiarCampos() {
            cfg.planilhaCaminho = campoPlanilha.getText().trim();
            cfg.empresa = campoEmpresa.getText().trim();
            cfg.tipo = campoTipo.getText().trim();
            cfg.competencia = campoCompetencia.getText().trim();
            cfg.pasta = campoPasta.getText().trim();
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
                preenchendo = true;
                try {
                    campoPlanilha.setText(cfg.planilhaCaminho);
                    campoEmpresa.setText(cfg.empresa);
                    campoTipo.setText(cfg.tipo);
                    campoCompetencia.setText(cfg.competencia);
                    campoPasta.setText(cfg.pasta);
                } finally {
                    preenchendo = false;
                }
                atualizarPrevia();
                LOG.fine("config lido de " + cfg.arquivo.getAbsolutePath());
                mostrarAbas();
                botaoGerar.setEnabled(true);
                estado("pronto", AZUL, AZUL_CLARO);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "falha ao ler o config", e);
                escrever("ERRO: " + mensagem(e), VERMELHO);
                escrever(CONTATO, CINZA_TEXTO);
                botaoGerar.setEnabled(false);
                estado("config com problema", VERMELHO, VERMELHO_FUNDO);
            }
        }

        /**
         * Confere a planilha em silencio. A linha so aparece quando ha algo a
         * resolver - planilha fora do lugar, ilegivel, ou sem a aba dos dados -
         * e ai mostra as abas que existem, que e quando isso serve para algo.
         */
        private void mostrarAbas() {
            String caminho = campoPlanilha.getText().trim();
            if (vazio(caminho)) {
                problemaNaPlanilha("Escolha a planilha no botao Selecionar...");
                return;
            }
            if (!new File(caminho).isFile()) {
                problemaNaPlanilha("Planilha nao encontrada neste caminho.");
                return;
            }
            List<String> avisos = new ArrayList<String>();
            try {
                LeitorPlanilha leitor = new LeitorPlanilha(new File(caminho), avisos);
                try {
                    List<String> abas = leitor.nomesDasAbas();
                    String procurada = cfg == null ? "Base" : cfg.abaBase;
                    boolean achou = false;
                    for (int i = 0; i < abas.size(); i++) {
                        if (abas.get(i).trim().equalsIgnoreCase(procurada.trim())) {
                            achou = true;
                        }
                    }
                    if (achou) {
                        rotuloAbas.setVisible(false);
                        rotuloAbasTitulo.setVisible(false);
                    } else {
                        problemaNaPlanilha("Nao achei a aba \"" + procurada
                                + "\". Nesta planilha existem: " + juntar(abas, ", "));
                    }
                } finally {
                    leitor.fechar();
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "falha ao listar as abas", e);
                problemaNaPlanilha("Nao consegui ler esta planilha.");
            }
        }

        private void problemaNaPlanilha(String texto) {
            rotuloAbasTitulo.setVisible(true);
            rotuloAbas.setVisible(true);
            rotuloAbas.setForeground(VERMELHO);
            rotuloAbas.setText(texto);
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

        private void escolherPasta() {
            JFileChooser seletor = new JFileChooser();
            seletor.setDialogTitle("Pasta onde salvar o txt");
            seletor.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            String atual = campoPasta.getText().trim();
            if (!vazio(atual)) {
                seletor.setCurrentDirectory(new File(atual));
            }
            if (seletor.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                campoPasta.setText(seletor.getSelectedFile().getAbsolutePath());
            }
        }

        private void gerarAgora() {
            if (cfg == null) {
                carregarConfig();
                if (cfg == null) {
                    return;
                }
            }
            copiarCampos();
            final String destino = "";
            botaoGerar.setEnabled(false);
            barra.setVisible(true);
            estado("gerando...", AZUL, AZUL_CLARO);
            escrever("gerando...", null);
            new SwingWorker<Resultado, Void>() {
                protected Resultado doInBackground() throws Exception {
                    return gerar(cfg, destino, new Confirmacao() {
                        public boolean sobrescrever(File arquivo) {
                            return perguntar("O arquivo ja existe:" + System.lineSeparator()
                                    + arquivo.getAbsolutePath() + System.lineSeparator()
                                    + System.lineSeparator() + "Sobrescrever?");
                        }

                        public boolean gerarDeNovo(File arquivo, String quando) {
                            return perguntar("Este mesmo arquivo ja foi gerado em " + quando + ":"
                                    + System.lineSeparator() + arquivo.getName()
                                    + System.lineSeparator() + System.lineSeparator()
                                    + "Confira se a competencia esta certa."
                                    + System.lineSeparator() + "Gerar de novo assim mesmo?");
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
                        campoPasta.setText(r.arquivo.getAbsoluteFile().getParent());
                        ultimoArquivo = r.arquivo;
                        atualizarPrevia();
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
                        if (desistencia(e)) {
                            escrever("geracao cancelada - nada foi gravado.", CINZA_TEXTO);
                            estado("cancelado", CINZA_TEXTO, AZUL_CLARO);
                            return;
                        }
                        LOG.log(Level.SEVERE, "falha ao gerar", e);
                        escrever("ERRO: " + mensagem(e), VERMELHO);
                        escrever(CONTATO, CINZA_TEXTO);
                        estado("falhou - veja o Registro e a aba Se der erro", VERMELHO,
                                VERMELHO_FUNDO);
                        JOptionPane.showMessageDialog(Janela.this,
                                caixaDeTexto(mensagem(e) + System.lineSeparator()
                                        + System.lineSeparator() + CONTATO),
                                NOME_PROGRAMA, JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }

        /** A pessoa respondeu "nao" numa pergunta: encerra quieto. */
        private static boolean desistencia(Throwable e) {
            Throwable atual = e;
            while (atual != null) {
                if (atual instanceof Cancelado) {
                    return true;
                }
                atual = atual.getCause();
            }
            return false;
        }

        /**
         * Texto comprido numa caixa de dialogo: sem largura fixa, a caixa
         * estica numa linha so e passa da tela.
         */
        private static JLabel caixaDeTexto(String texto) {
            StringBuilder sb = new StringBuilder("<html><div style='width:360px'>");
            for (int i = 0; i < texto.length(); i++) {
                char c = texto.charAt(i);
                if (c == '&') {
                    sb.append("&amp;");
                } else if (c == '<') {
                    sb.append("&lt;");
                } else if (c == '>') {
                    sb.append("&gt;");
                } else if (c == '\n') {
                    sb.append("<br>");
                } else if (c != '\r') {
                    sb.append(c);
                }
            }
            return new JLabel(sb.append("</div></html>").toString());
        }

        /** Pergunta de sim/nao na tela, venha de qual thread vier. */
        private boolean perguntar(final String texto) {
            final boolean[] resposta = new boolean[1];
            Runnable pergunta = new Runnable() {
                public void run() {
                    int escolha = JOptionPane.showConfirmDialog(Janela.this,
                            caixaDeTexto(texto), NOME_PROGRAMA, JOptionPane.YES_NO_OPTION,
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
                LOG.log(Level.WARNING, "falha ao mostrar a pergunta", e);
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
                + "<h2 style='color:#102E54; margin-bottom:2px'>Como usar</h2> "
                + "<div style='color:#5F6976'>O passo a passo, do comeco ao arquivo pronto.</div> "
                + "<hr> "
                + "<p>O programa l&ecirc; a sua planilha e grava o arquivo de texto que o sistema "
                + "importa. "
                + "Ele <b>nunca altera a planilha</b> &mdash; pode at&eacute; deix&aacute;-la aberta no "
                + "Excel.</p> "
                + "<h3 style='color:#1D5B9A'>Antes de come&ccedil;ar</h3> "
                + "<p>A planilha precisa ter a aba <b>Base</b> preenchida: uma linha por registro, das "
                + "colunas <b>A at&eacute; I</b>, a partir da <b>linha 2</b>. A linha 1 &eacute; o "
                + "cabe&ccedil;alho.</p> "
                + "<h3 style='color:#1D5B9A'>Passo 1 &mdash; apontar a planilha</h3> "
                + "<p>No campo <b>Planilha</b>, clique em <b>Selecionar...</b> e escolha o arquivo. "
                + "Se aparecer uma linha vermelha, &eacute; porque o arquivo n&atilde;o est&aacute; "
                + "l&aacute; ou n&atilde;o tem a aba "
                + "Base &mdash; leia o que ela diz.</p> "
                + "<p>Depois da primeira vez o caminho fica guardado: nos meses seguintes j&aacute; vem "
                + "preenchido.</p> "
                + "<h3 style='color:#1D5B9A'>Passo 2 &mdash; preencher os tr&ecirc;s campos</h3> "
                + "<table cellpadding='4' cellspacing='0'> "
                + "<tr><td><b>Empresa*</b></td><td>o c&oacute;digo da empresa, por exemplo "
                + "<b>744</b></td></tr> "
                + "<tr><td><b>Tipo*</b></td><td>o que est&aacute; sendo importado, por exemplo "
                + "<b>PARCELAMENTOS</b></td></tr> "
                + "<tr><td><b>Compet&ecirc;ncia</b></td><td>o m&ecirc;s, por exemplo "
                + "<b>082025</b></td></tr> "
                + "</table> "
                + "<p>Os dois com asterisco s&atilde;o obrigat&oacute;rios. <b>&Eacute; deles que sai o "
                + "nome do arquivo</b>:</p> "
                + "<pre style='background:#F2F5F9; padding:6px; font-size:11px'>744 + PARCELAMENTOS + "
                + "082025  =  744_PARCELAMENTOS_082025.txt</pre> "
                + "<h3 style='color:#1D5B9A'>Passo 3 &mdash; escolher a pasta</h3> "
                + "<p>No campo <b>Pasta</b>, clique em <b>Selecionar...</b> e escolha onde o arquivo "
                + "deve ser gravado. Tamb&eacute;m fica guardado para as pr&oacute;ximas vezes.</p> "
                + "<h3 style='color:#1D5B9A'>Passo 4 &mdash; conferir a linha azul</h3> "
                + "<p style='background:#EAF1F9; padding:6px'>A linha azul, logo abaixo dos campos, "
                + "mostra <b>o arquivo exato</b> que vai ser gravado, com pasta e nome completos. "
                + "<b>Leia essa linha antes de clicar.</b> &Eacute; a sua &uacute;ltima chance de notar "
                + "um m&ecirc;s "
                + "errado ou uma pasta errada.</p> "
                + "<p>Se aparecer uma linha laranja avisando que esse arquivo <b>j&aacute; foi "
                + "gerado</b> em "
                + "tal data, pare e confira a compet&ecirc;ncia: quase sempre &eacute; o m&ecirc;s que "
                + "ficou do "
                + "m&ecirc;s passado.</p> "
                + "<h3 style='color:#1D5B9A'>Passo 5 &mdash; gerar</h3> "
                + "<p>Clique em <b>Gerar arquivo</b> (ou aperte <b>Enter</b>). Leva menos de um "
                + "segundo.</p> "
                + "<h3 style='color:#1D5B9A'>Passo 6 &mdash; ler o resultado</h3> "
                + "<p>O quadro <b>Registro</b> conta o que aconteceu, e a cor j&aacute; diz o que "
                + "&eacute;:</p> "
                + "<table cellpadding='5' cellspacing='0'> "
                + "<tr style='background:#E8F4EC'><td><b style='color:#00743E'>verde</b></td> "
                + "    <td>deu certo. Mostra quantos registros e quantos bytes</td></tr> "
                + "<tr style='background:#FFF6E5'><td><b style='color:#B56500'>laranja</b></td> "
                + "    <td>gerou, mas tem algo para voc&ecirc; conferir na planilha</td></tr> "
                + "<tr style='background:#FDEBEB'><td><b style='color:#B01C1C'>vermelho</b></td> "
                + "    <td>n&atilde;o gerou nada. A aba <b>Se der erro</b> explica o que fazer</td></tr> "
                + "</table> "
                + "<p><b>Aviso laranja n&atilde;o &eacute; erro</b>, mas tamb&eacute;m n&atilde;o "
                + "&eacute; para ignorar: ele aponta a "
                + "c&eacute;lula exata que merece um olhar.</p> "
                + "<h3 style='color:#1D5B9A'>Passo 7 &mdash; conferir o arquivo</h3> "
                + "<p><b>Abrir txt</b> abre o arquivo gerado; <b>Abrir pasta</b> abre a pasta dele. "
                + "Na primeira vez, vale abrir e dar uma olhada antes de importar no sistema.</p> "
                + "<h3 style='color:#1D5B9A'>No m&ecirc;s seguinte</h3> "
                + "<p>Abra o programa: planilha, empresa, tipo e pasta j&aacute; v&ecirc;m preenchidos. "
                + "<b>Normalmente s&oacute; a compet&ecirc;ncia muda.</b> Troque o m&ecirc;s, confira a "
                + "linha azul e "
                + "gere.</p> "
                + "<h3 style='color:#1D5B9A'>Os bot&otilde;es</h3> "
                + "<table cellpadding='5' cellspacing='0'> "
                + "<tr><td><b>Gerar arquivo</b></td><td>l&ecirc; a planilha e grava o txt &nbsp;(atalho: "
                + "Enter)</td></tr> "
                + "<tr style='background:#FAFBFD'><td><b>Abrir txt</b></td><td>abre o arquivo que acabou "
                + "de ser gerado</td></tr> "
                + "<tr><td><b>Abrir pasta</b></td><td>abre a pasta onde ele foi gravado</td></tr> "
                + "<tr style='background:#FAFBFD'><td><b>Recarregar config</b></td><td>l&ecirc; de novo "
                + "as configura&ccedil;&otilde;es, se algu&eacute;m as mudou por fora</td></tr> "
                + "</table> "
                + "<h3 style='color:#B01C1C'>Se aparecer erro</h3> "
                + "<p>Leia a mensagem em vermelho e veja a aba <b>Se der erro</b>: as causas comuns "
                + "est&atilde;o l&aacute;, com o que fazer em cada uma.</p> "
                + "<p style='background:#FDEBEB; padding:6px'>Se n&atilde;o resolver, <b>fale com Ronald "
                + "Lira</b>, "
                + "que fez o programa. Leve junto o arquivo <b>gerador_arquivo.log</b>, que fica na "
                + "pasta do programa &mdash; o caminho est&aacute; no rodap&eacute; desta janela. Esse "
                + "arquivo guarda "
                + "o erro completo, com data e hora, e &eacute; o que resolve a d&uacute;vida mais "
                + "r&aacute;pido.</p> "
                + "</body></html> ";
        }

        private static String textoSeDerErro() {
            return ""
                + "<html><body style='font-family:sans-serif; font-size:12px; margin:4px 10px 10px "
                + "10px'> "
                + "<h2 style='color:#102E54; margin-bottom:2px'>Se der erro</h2> "
                + "<div style='color:#5F6976'>O que costuma dar errado, o que fazer, e a quem "
                + "recorrer.</div> "
                + "<hr> "
                + "<p><b>Primeiro:</b> leia a linha vermelha no quadro <b>Registro</b>. Ela diz o que "
                + "aconteceu, em portugu&ecirc;s. Quase sempre a resposta est&aacute; na tabela "
                + "abaixo.</p> "
                + "<h3 style='color:#B01C1C'>N&atilde;o gerou nada</h3> "
                + "<table cellpadding='5' cellspacing='0'> "
                + "<tr style='background:#F2F5F9'><td><b>O que a mensagem diz</b></td><td><b>O que "
                + "fazer</b></td></tr> "
                + "<tr><td><b>N&atilde;o encontrei a planilha</b></td> "
                + "    <td>algu&eacute;m moveu, renomeou, ou a rede caiu. Clique em <b>Selecionar...</b> "
                + "e "
                + "    aponte o arquivo de novo</td></tr> "
                + "<tr style='background:#FAFBFD'><td><b>N&atilde;o parece uma planilha do "
                + "Excel</b></td> "
                + "    <td>&eacute; um arquivo <b>.xls</b> antigo. Abra no Excel e salve como "
                + "    <b>.xlsx</b> ou <b>.xlsm</b></td></tr> "
                + "<tr><td><b>A aba Base n&atilde;o existe</b></td> "
                + "    <td>a aba foi renomeada ou tem espa&ccedil;o sobrando no nome. A mensagem lista "
                + "as "
                + "    abas que existem na planilha</td></tr> "
                + "<tr style='background:#FAFBFD'><td><b>Preencha Empresa</b> / <b>Preencha "
                + "Tipo</b></td> "
                + "    <td>s&atilde;o obrigat&oacute;rios: &eacute; deles que sai o nome do "
                + "arquivo</td></tr> "
                + "<tr><td><b>Escolha a pasta</b></td><td>falta dizer onde gravar</td></tr> "
                + "<tr style='background:#FAFBFD'><td><b>A aba Base n&atilde;o tem nenhuma linha "
                + "preenchida</b></td> "
                + "    <td>confira se os dados n&atilde;o foram colados em outra aba</td></tr> "
                + "<tr><td><b>Tem um caractere que n&atilde;o existe em windows-1252</b></td> "
                + "    <td>algu&eacute;m colou um emoji ou um s&iacute;mbolo estranho de um site. A "
                + "mensagem diz a "
                + "    linha; apague o caractere na planilha. Acento comum, &ccedil; e &atilde; "
                + "    podem ficar</td></tr> "
                + "<tr style='background:#FAFBFD'><td><b>O arquivo j&aacute; existe. Voc&ecirc; deve "
                + "exclu&iacute;-lo</b></td> "
                + "    <td>&eacute; de prop&oacute;sito, para n&atilde;o apagar sem querer um arquivo "
                + "bom. Apague o txt "
                + "    antigo e gere de novo</td></tr> "
                + "<tr><td><b>N&atilde;o consegui criar a pasta</b></td> "
                + "    <td>a unidade de rede est&aacute; fora do ar, ou voc&ecirc; n&atilde;o tem "
                + "permiss&atilde;o nela</td></tr> "
                + "</table> "
                + "<h3 style='color:#B56500'>Gerou, mas avisou em laranja</h3> "
                + "<p>O arquivo est&aacute; gravado. O aviso aponta algo na planilha que merece "
                + "confer&ecirc;ncia:</p> "
                + "<ul> "
                + "<li><b>F&oacute;rmula sem valor calculado</b> &mdash; abra a planilha no Excel, deixe "
                + "    calcular e salve. O campo saiu vazio no arquivo.</li> "
                + "<li><b>Barra vertical no meio do texto</b> &mdash; o <code>|</code> separa os campos "
                + "    do arquivo, ent&atilde;o ele foi trocado por espa&ccedil;o. O aviso diz em qual "
                + "c&eacute;lula.</li> "
                + "<li><b>Coluna A vazia com dados no resto da linha</b> &mdash; pode ser dado colado "
                + "    na linha errada. A linha foi gravada assim mesmo.</li> "
                + "<li><b>Este arquivo j&aacute; foi gerado em tal data</b> &mdash; confira a "
                + "compet&ecirc;ncia "
                + "    antes de gravar por cima.</li> "
                + "</ul> "
                + "<h3 style='color:#00743E'>Quando o arquivo sai certo mas o conte&uacute;do "
                + "est&aacute; errado</h3> "
                + "<p>Estas quatro coisas o programa <b>n&atilde;o tem como perceber</b>. Se o sistema "
                + "recusar a importa&ccedil;&atilde;o, ou os valores sa&iacute;rem estranhos, comece por "
                + "aqui:</p> "
                + "<ul> "
                + "<li><b>O que vale &eacute; o valor, n&atilde;o o que aparece na tela.</b> Uma "
                + "c&eacute;lula que mostra "
                + "    <b>1.234,50</b> pode ter o valor 1234,5 &mdash; e &eacute; 1234,5 que vai para o "
                + "    arquivo.</li> "
                + "<li><b>Data tem de ser data de verdade.</b> Se foi digitada como texto, sai como "
                + "    est&aacute; escrita (<b>31.01.26</b> continua <b>31.01.26</b>). E data numa "
                + "c&eacute;lula "
                + "    formatada como Geral sai como n&uacute;mero (<b>46053</b>).</li> "
                + "<li><b>C&eacute;lula mesclada</b> guarda o valor s&oacute; na primeira c&eacute;lula; "
                + "as outras v&atilde;o "
                + "    vazias para o arquivo.</li> "
                + "<li><b>Linha escondida por filtro &eacute; lida do mesmo jeito.</b> O filtro esconde "
                + "da "
                + "    sua vista, n&atilde;o do programa.</li> "
                + "</ul> "
                + "<h3 style='color:#1D5B9A'>Nada disso resolveu</h3> "
                + "<p style='background:#FDEBEB; padding:6px'><b>Fale com Ronald Lira</b>, que fez o "
                + "programa.<br><br> "
                + "Leve junto o arquivo <b>gerador_arquivo.log</b>, da pasta do programa &mdash; o "
                + "caminho completo est&aacute; no rodap&eacute; desta janela. Ele guarda todo erro com "
                + "data e "
                + "hora, e poupa muito tempo de adivinha&ccedil;&atilde;o.<br><br> "
                + "Se puder, diga tamb&eacute;m: o que voc&ecirc; estava gerando, qual planilha, e o que "
                + "a linha "
                + "vermelha dizia.</p> "
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
        // O SwingWorker embrulha o erro; sem desembrulhar, o nome da classe
        // aparece na tela na frente da mensagem.
        if (e instanceof java.util.concurrent.ExecutionException && e.getCause() != null) {
            return mensagem(e.getCause());
        }
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
