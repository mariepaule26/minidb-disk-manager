import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Tests du DiskManager.
 * Les tests ne dependent que des interfaces IDiskManager, IPageId et de DiskManagerException
 * fournies par le professeur, ainsi que du constructeur sans argument de DiskManager.
 * Aucun nom de fichier interne n'est suppose.
 *
 * Deux categories :
 *  - tests obligatoires : deduits de l'enonce et des complements, un echec est une erreur (ECHEC)
 *  - tests de robustesse : comportements raisonnables mais non precises par l'enonce,
 *    un echec est un simple avertissement (AVERT)
 *
 * Chaque test s'execute dans un dossier temporaire neuf, supprime a la fin du test.
 * Le code de retour du programme est 1 si au moins un test obligatoire echoue.
 */
public class DiskManagerTests {

    // ------------------------------------------------------------------
    // Mini infrastructure de test
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface Test {
        void run() throws Exception;
    }

    private static class Echec extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Echec(String message) {
            super(message);
        }
    }

    private static int nbOk = 0;
    private static int nbEchecs = 0;
    private static int nbAvertissements = 0;
    private static int nbNettoyagesIncomplets = 0;
    private static Path racine;

    public static void main(String[] args) {
        System.out.println("=== Tests obligatoires : identifiants et Init ===");
        obligatoire("TestEqualsHashCode", DiskManagerTests::TestEqualsHashCode);
        obligatoire("TestInitDossierVide", DiskManagerTests::TestInitDossierVide);
        obligatoire("TestInitCreeDossierInexistant", DiskManagerTests::TestInitCreeDossierInexistant);
        obligatoire("TestSecondInitEchoue", DiskManagerTests::TestSecondInitEchoue);
        obligatoire("TestGetPageSize", DiskManagerTests::TestGetPageSize);
        obligatoire("TestTaillePageIncompatible", DiskManagerTests::TestTaillePageIncompatible);

        System.out.println();
        System.out.println("=== Tests obligatoires : lecture et ecriture ===");
        obligatoire("TestEcritureLecture", DiskManagerTests::TestEcritureLecture);
        obligatoire("TestValeursExtremes", DiskManagerTests::TestValeursExtremes);
        obligatoire("TestTaillesDePage", DiskManagerTests::TestTaillesDePage);
        obligatoire("TestPageDeTailleUnOctet", DiskManagerTests::TestPageDeTailleUnOctet);
        obligatoire("TestPagesIndependantes", DiskManagerTests::TestPagesIndependantes);
        obligatoire("TestNonDebordement", DiskManagerTests::TestNonDebordement);
        obligatoire("TestEcrasement", DiskManagerTests::TestEcrasement);
        obligatoire("TestLectureRemplitToutLeBuffer", DiskManagerTests::TestLectureRemplitToutLeBuffer);
        obligatoire("TestEcritureNeModifiePasLeBuffer", DiskManagerTests::TestEcritureNeModifiePasLeBuffer);

        System.out.println();
        System.out.println("=== Tests obligatoires : allocation et desallocation ===");
        obligatoire("TestAllocDistinctes", DiskManagerTests::TestAllocDistinctes);
        obligatoire("TestAllocDeallocRealloc", DiskManagerTests::TestAllocDeallocRealloc);
        obligatoire("TestReallocationPlusieursLibres", DiskManagerTests::TestReallocationPlusieursLibres);
        obligatoire("TestDeallocToutPuisRealloc", DiskManagerTests::TestDeallocToutPuisRealloc);
        obligatoire("TestReallocPageEcritureFraiche", DiskManagerTests::TestReallocPageEcritureFraiche);
        obligatoire("TestGetActivePageCount", DiskManagerTests::TestGetActivePageCount);
        obligatoire("TestGrosVolume", DiskManagerTests::TestGrosVolume);

        System.out.println();
        System.out.println("=== Tests obligatoires : Save et persistance ===");
        obligatoire("TestSaveDossierVide", DiskManagerTests::TestSaveDossierVide);
        obligatoire("TestSaveIdempotent", DiskManagerTests::TestSaveIdempotent);
        obligatoire("TestPersistanceContenu", DiskManagerTests::TestPersistanceContenu);
        obligatoire("TestPersistancePagesLibres", DiskManagerTests::TestPersistancePagesLibres);
        obligatoire("TestPersistanceNouvellesAllocs", DiskManagerTests::TestPersistanceNouvellesAllocs);
        obligatoire("TestPersistanceCompteurPages", DiskManagerTests::TestPersistanceCompteurPages);
        obligatoire("TestPersistanceMultiCycles", DiskManagerTests::TestPersistanceMultiCycles);
        obligatoire("TestDeuxDiskManagersIndependants", DiskManagerTests::TestDeuxDiskManagersIndependants);

        System.out.println();
        System.out.println("=== Tests obligatoires : stress aleatoire compare a un modele ===");
        obligatoire("TestStressTaille4", () -> stress(1L, 4, 4000));
        obligatoire("TestStressTaille16", () -> stress(2L, 16, 4000));
        obligatoire("TestStressTaille1", () -> stress(3L, 1, 4000));
        obligatoire("TestStressTaille128", () -> stress(4L, 128, 4000));

        System.out.println();
        System.out.println("=== Tests de robustesse (avertissements) ===");
        souple("TestOperationsAvantInit", DiskManagerTests::TestOperationsAvantInit);
        souple("TestInitArgumentsInvalides", DiskManagerTests::TestInitArgumentsInvalides);
        souple("TestInitDossierImbrique", DiskManagerTests::TestInitDossierImbrique);
        souple("TestSecondInitSansEffetDeBord", DiskManagerTests::TestSecondInitSansEffetDeBord);
        souple("TestIdentifiantNull", DiskManagerTests::TestIdentifiantNull);
        souple("TestIdentifiantEtranger", DiskManagerTests::TestIdentifiantEtranger);
        souple("TestIdentifiantHorsBornes", DiskManagerTests::TestIdentifiantHorsBornes);
        souple("TestPageLibereeInutilisable", DiskManagerTests::TestPageLibereeInutilisable);
        souple("TestDoubleDesallocation", DiskManagerTests::TestDoubleDesallocation);
        souple("TestBufferInvalide", DiskManagerTests::TestBufferInvalide);
        souple("TestPageFraicheLisibleEtNulle", DiskManagerTests::TestPageFraicheLisibleEtNulle);
        souple("TestBufferDirect", DiskManagerTests::TestBufferDirect);

        System.out.println();
        if (nbNettoyagesIncomplets > 0) {
            System.out.println("[INFO]   " + nbNettoyagesIncomplets + " dossier(s) temporaire(s) non supprime(s) "
                    + "(fichiers encore ouverts par le DiskManager, sans incidence sur les resultats)");
        }
        System.out.println("Bilan : " + nbOk + " OK, " + nbEchecs + " ECHEC, " + nbAvertissements + " AVERT");
        if (nbEchecs > 0) {
            System.exit(1);
        }
    }

    private static void obligatoire(String nom, Test test) {
        executer(nom, test, true);
    }

    private static void souple(String nom, Test test) {
        executer(nom, test, false);
    }

    private static void executer(String nom, Test test, boolean obligatoire) {
        try {
            racine = Files.createTempDirectory("dmtests_");
        } catch (IOException e) {
            throw new RuntimeException("Impossible de creer le dossier temporaire", e);
        }
        long debut = System.currentTimeMillis();
        try {
            test.run();
            nbOk++;
            System.out.println("[OK]     " + nom + " (" + (System.currentTimeMillis() - debut) + " ms)");
        } catch (Throwable t) {
            String message = (t instanceof Echec) ? t.getMessage()
                    : t.getClass().getSimpleName() + ": " + t.getMessage() + " " + emplacement(t);
            if (obligatoire) {
                nbEchecs++;
                System.out.println("[ECHEC]  " + nom + " -> " + message);
            } else {
                nbAvertissements++;
                System.out.println("[AVERT]  " + nom + " -> " + message);
            }
        } finally {
            nettoyer(racine);
        }
    }

    /** Premiere ligne de la trace appartenant au code du projet (hors tests). */
    private static String emplacement(Throwable t) {
        for (StackTraceElement e : t.getStackTrace()) {
            String c = e.getClassName();
            if (c.equals("DiskManager") || c.equals("PageId")) {
                return "(" + c + "." + e.getMethodName() + ":" + e.getLineNumber() + ")";
            }
        }
        return "";
    }

    private static void nettoyer(Path dossier) {
        if (!supprimer(dossier)) {
            System.gc();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!supprimer(dossier)) {
                nbNettoyagesIncomplets++;
            }
        }
    }

    private static boolean supprimer(Path dossier) {
        try {
            Files.walkFileTree(dossier, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                    Files.deleteIfExists(f);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                    Files.deleteIfExists(d);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Assertions
    // ------------------------------------------------------------------

    private static void verifier(boolean condition, String message) {
        if (!condition) {
            throw new Echec(message);
        }
    }

    private static void verifierEgal(Object attendu, Object obtenu, String message) {
        if (!Objects.equals(attendu, obtenu)) {
            throw new Echec(message + " : attendu " + attendu + ", obtenu " + obtenu);
        }
    }

    private static void verifierOctets(byte[] attendu, byte[] obtenu, String message) {
        if (!Arrays.equals(attendu, obtenu)) {
            throw new Echec(message + " : attendu " + abrege(attendu) + ", obtenu " + abrege(obtenu));
        }
    }

    /**
     * Verifie que l'action leve bien une DiskManagerException (et pas une autre exception,
     * ni rien du tout).
     */
    private static void exigerErreur(String contexte, Test action) {
        try {
            action.run();
        } catch (DiskManagerException e) {
            return;
        } catch (Echec e) {
            throw e;
        } catch (Throwable t) {
            throw new Echec(contexte + " : une DiskManagerException est attendue, mais "
                    + t.getClass().getSimpleName() + " a ete levee");
        }
        throw new Echec(contexte + " : aucune erreur signalee, une DiskManagerException est attendue");
    }

    private static String abrege(byte[] b) {
        StringBuilder sb = new StringBuilder("[");
        int n = Math.min(b.length, 16);
        for (int i = 0; i < n; i++) {
            sb.append(String.format("%02x", b[i] & 0xff));
            if (i < n - 1) {
                sb.append(' ');
            }
        }
        if (b.length > n) {
            sb.append(" ... (").append(b.length).append(" octets)");
        }
        return sb.append("]").toString();
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private static Path nouveauDossier() throws IOException {
        return Files.createTempDirectory(racine, "dm");
    }

    private static String chemin(Path dossier) {
        return dossier.toAbsolutePath().toString();
    }

    private static IDiskManager nouveauDM(Path dossier, int taillePage) throws DiskManagerException {
        IDiskManager dm = new DiskManager();
        dm.Init(chemin(dossier), taillePage);
        return dm;
    }

    /** Contenu deterministe de taille donnee, different pour chaque graine. */
    private static byte[] motif(int taille, int graine) {
        byte[] b = new byte[taille];
        for (int i = 0; i < taille; i++) {
            b[i] = (byte) (graine * 31 + i * 7 + 1);
        }
        return b;
    }

    private static byte[] rempli(int taille, int valeur) {
        byte[] b = new byte[taille];
        Arrays.fill(b, (byte) valeur);
        return b;
    }

    private static void ecrire(IDiskManager dm, IPageId id, byte[] donnees) throws DiskManagerException {
        dm.WritePage(id, ByteBuffer.wrap(donnees.clone()));
    }

    private static byte[] lire(IDiskManager dm, IPageId id, int taille) throws DiskManagerException {
        ByteBuffer bb = ByteBuffer.allocate(taille);
        dm.ReadPage(id, bb);
        return bb.array();
    }

    private static List<IPageId> allouer(IDiskManager dm, int n) throws DiskManagerException {
        List<IPageId> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            IPageId id = dm.AllocPage();
            verifier(id != null, "AllocPage a renvoye null (appel numero " + i + ")");
            ids.add(id);
        }
        return ids;
    }

    private static Set<IPageId> ensemble(List<IPageId> ids) {
        return new HashSet<>(ids);
    }

    /**
     * Construit un PageId de numero arbitraire par reflexion, pour ne pas dependre
     * de la signature exacte du constructeur de PageId. Renvoie null si impossible.
     */
    private static IPageId pageIdArbitraire(int numero) {
        try {
            return (IPageId) Class.forName("PageId").getConstructor(int.class).newInstance(numero);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Tests obligatoires : identifiants et Init
    // ------------------------------------------------------------------

    static void TestEqualsHashCode() throws Exception {
        IDiskManager a = nouveauDM(nouveauDossier(), 4);
        IDiskManager b = nouveauDM(nouveauDossier(), 4);
        IPageId pa = a.AllocPage();
        IPageId pb = b.AllocPage();
        verifier(pa != null && pb != null, "AllocPage renvoie null");
        verifier(pa.equals(pa), "equals doit etre reflexif");
        verifier(pa.equals(pb) && pb.equals(pa),
                "Les premieres pages de deux gestionnaires neufs doivent etre egales (equals symetrique)");
        verifierEgal(pa.hashCode(), pb.hashCode(),
                "hashCode doit etre identique pour deux identifiants egaux (PageId doit redefinir hashCode)");
        verifier(!pa.equals(null), "equals(null) doit renvoyer false");
        verifier(!pa.equals("texte"), "equals avec un objet d'un autre type doit renvoyer false");
        IPageId pa2 = a.AllocPage();
        verifier(!pa.equals(pa2), "Deux pages differentes ne doivent pas avoir des identifiants egaux");
    }

    static void TestInitDossierVide() throws Exception {
        IDiskManager dm = nouveauDM(nouveauDossier(), 4);
        verifier(dm.AllocPage() != null, "AllocPage renvoie null apres Init sur dossier vide");
    }

    static void TestInitCreeDossierInexistant() throws Exception {
        Path d = racine.resolve("dossier_inexistant");
        verifier(!Files.exists(d), "pre-condition : le dossier ne doit pas exister");
        IDiskManager dm = new DiskManager();
        dm.Init(chemin(d), 4);
        verifier(Files.isDirectory(d), "Init doit creer le dossier de travail s'il n'existe pas");
        IPageId p = dm.AllocPage();
        ecrire(dm, p, new byte[]{1, 2, 3, 4});
        dm.Save();
        IDiskManager dm2 = nouveauDM(d, 4);
        verifierOctets(new byte[]{1, 2, 3, 4}, lire(dm2, p, 4), "Donnees relues apres creation du dossier par Init");
    }

    static void TestSecondInitEchoue() throws Exception {
        Path a = nouveauDossier();
        Path b = nouveauDossier();
        IDiskManager dm = nouveauDM(a, 4);
        exigerErreur("Second Init avec le meme dossier et la meme taille", () -> dm.Init(chemin(a), 4));
        exigerErreur("Second Init avec un autre dossier", () -> dm.Init(chemin(b), 4));
        exigerErreur("Second Init avec une autre taille de page", () -> dm.Init(chemin(a), 8));
    }

    static void TestGetPageSize() throws Exception {
        int[] tailles = {1, 4, 64, 4096};
        for (int t : tailles) {
            IDiskManager dm = nouveauDM(nouveauDossier(), t);
            verifierEgal(t, dm.GetPageSize(), "GetPageSize apres Init(" + t + ")");
        }
    }

    static void TestTaillePageIncompatible() throws Exception {
        Path dossier = nouveauDossier();
        IDiskManager dm = nouveauDM(dossier, 4);
        IPageId p = dm.AllocPage();
        ecrire(dm, p, new byte[]{1, 2, 3, 4});
        dm.Save();
        exigerErreur("Init avec une taille de page 8 sur des donnees de taille 4",
                () -> new DiskManager().Init(chemin(dossier), 8));
        exigerErreur("Init avec une taille de page 2 sur des donnees de taille 4",
                () -> new DiskManager().Init(chemin(dossier), 2));
        // Un refus ne doit pas abimer les donnees existantes
        IDiskManager dm2 = nouveauDM(dossier, 4);
        verifierOctets(new byte[]{1, 2, 3, 4}, lire(dm2, p, 4),
                "Donnees alterees par un Init refuse pour taille de page incompatible");
    }

    // ------------------------------------------------------------------
    // Tests obligatoires : lecture et ecriture
    // ------------------------------------------------------------------

    static void TestEcritureLecture() throws Exception {
        IDiskManager dm = nouveauDM(nouveauDossier(), 4);
        IPageId p = dm.AllocPage();
        byte[] d = {1, 2, 3, 4};
        ecrire(dm, p, d);
        verifierOctets(d, lire(dm, p, 4), "Le contenu relu differe du contenu ecrit");
    }

    static void TestValeursExtremes() throws Exception {
        int t = 256;
        IDiskManager dm = nouveauDM(nouveauDossier(), t);
        byte[] tous = new byte[t];
        for (int i = 0; i < t; i++) {
            tous[i] = (byte) i;
        }
        byte[] alterne = new byte[t];
        for (int i = 0; i < t; i++) {
            alterne[i] = (byte) (i % 2 == 0 ? 0x55 : 0xAA);
        }
        byte[][] contenus = {tous, rempli(t, 0x00), rempli(t, 0xFF), rempli(t, Byte.MIN_VALUE),
                rempli(t, Byte.MAX_VALUE), alterne};
        List<IPageId> ids = allouer(dm, contenus.length);
        for (int i = 0; i < contenus.length; i++) {
            ecrire(dm, ids.get(i), contenus[i]);
        }
        for (int i = 0; i < contenus.length; i++) {
            verifierOctets(contenus[i], lire(dm, ids.get(i), t), "Valeurs extremes, contenu numero " + i);
        }
    }

    static void TestTaillesDePage() throws Exception {
        int[] tailles = {1, 2, 3, 4, 7, 8, 64, 100, 512, 4096, 65536};
        for (int t : tailles) {
            IDiskManager dm = nouveauDM(nouveauDossier(), t);
            List<IPageId> ids = allouer(dm, 3);
            for (int i = 0; i < 3; i++) {
                ecrire(dm, ids.get(i), motif(t, i + 1));
            }
            for (int i = 0; i < 3; i++) {
                verifierOctets(motif(t, i + 1), lire(dm, ids.get(i), t),
                        "Taille de page " + t + ", page " + i);
            }
        }
    }

    static void TestPageDeTailleUnOctet() throws Exception {
        IDiskManager dm = nouveauDM(nouveauDossier(), 1);
        int n = 300;
        List<IPageId> ids = allouer(dm, n);
        for (int i = 0; i < n; i++) {
            ecrire(dm, ids.get(i), new byte[]{(byte) i});
        }
        for (int i = 0; i < n; i++) {
            verifierOctets(new byte[]{(byte) i}, lire(dm, ids.get(i), 1), "Page de 1 octet numero " + i);
        }
    }

    static void TestPagesIndependantes() throws Exception {
        int n = 50;
        IDiskManager dm = nouveauDM(nouveauDossier(), 4);
        List<IPageId> ids = allouer(dm, n);
        for (int i = 0; i < n; i++) {
            ecrire(dm, ids.get(i), motif(4, i));
        }
        List<Integer> ordre = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ordre.add(i);
        }
        Collections.shuffle(ordre, new Random(42));
        for (int i : ordre) {
            verifierOctets(motif(4, i), lire(dm, ids.get(i), 4), "Page " + i + " alteree par une autre page");
        }
    }

    static void TestNonDebordement() throws Exception {
        int t = 8;
        int n = 7;
        IDiskManager dm = nouveauDM(nouveauDossier(), t);
        List<IPageId> ids = allouer(dm, n);
        for (IPageId id : ids) {
            ecrire(dm, id, rempli(t, 0x11));
        }
        for (int k = 0; k < n; k++) {
            ecrire(dm, ids.get(k), rempli(t, 0xFF));
            for (int j = 0; j < n; j++) {
                byte[] attendu = rempli(t, j == k ? 0xFF : 0x11);
                verifierOctets(attendu, lire(dm, ids.get(j), t),
                        "Ecriture dans la page " + k + " : la page " + j + " est incorrecte");
            }
            ecrire(dm, ids.get(k), rempli(t, 0x11));
        }
    }

    static void TestEcrasement() throws Exception {
        IDiskManager dm = nouveauDM(nouveauDossier(), 4);
        List<IPageId> ids = allouer(dm, 3);
        for (int i = 0; i < 3; i++) {
            ecrire(dm, ids.get(i), motif(4, i));
        }
        ecrire(dm, ids.get(1), new byte[]{9, 9, 9, 9});
        ecrire(dm, ids.get(1), new byte[]{5, 6, 7, 8});
        verifierOctets(new byte[]{5, 6, 7, 8}, lire(dm, ids.get(1), 4), "Seconde ecriture non prise en compte");
        verifierOctets(motif(4, 0), lire(dm, ids.get(0), 4), "Page 0 modifiee par l'ecrasement de la page 1");
        verifierOctets(motif(4, 2), lire(dm, ids.get(2), 4), "Page 2 modifiee par l'ecrasement de la page 1");
    }

    static void TestLectureRemplitToutLeBuffer() throws Exception {
        int t = 8;
        IDiskManager dm = nouveauDM(nouveauDossier(), t);
        IPageId p = dm.AllocPage();
        byte[] contenu = {0, 1, 2, 3, 4, 5, 6, 0};
        ecrire(dm, p, contenu);
        byte[] tableau = rempli(t, 0x55);
        dm.ReadPage(p, ByteBuffer.wrap(tableau));
        verifierOctets(contenu, tableau, "ReadPage doit ecraser tout le buffer, y compris les octets nuls");
    }

    static void TestEcritureNeModifiePasLeBuffer() throws Exception {
        IDiskManager dm = nouveauDM(nouveauDossier(), 8);
        IPageId p = dm.AllocPage();
        byte[] contenu = motif(8, 3);
        byte[] copie = contenu.clone();
        dm.WritePage(p, ByteBuffer.wrap(contenu));
        verifierOctets(copie, contenu, "WritePage a modifie le contenu du buffer de l'appelant");
    }

    // ------------------------------------------------------------------
    // Tests obligatoires : allocation et desallocation
    // ------------------------------------------------------------------

    static void TestAllocDistinctes() throws Exception {
        IDiskManager dm = nouveauDM(nouveauDossier(), 4);
        List<IPageId> ids = allouer(dm, 2000);
        verifierEgal(2000, ensemble(ids).size(), "Nombre d'identifiants distincts sur 2000 allocations");
    }

    static void TestAllocDeallocRealloc() throws Exception {
        IDiskManager dm = nouveauDM(nouveauDossier(), 4);
        List<IPageId> ids = allouer(dm, 3);
        dm.DeallocPage(ids.get(1));
        IPageId r = dm.AllocPage();
        verifierEgal(ids.get(1), r, "La page desallouee doit etre reutilisee en priorite");
        IPageId suivante = dm.AllocPage();
        verifier(!ensemble(ids).contains(suivante),
                "Sans page libre, une page nouvelle est attendue, mais " + suivante + " est deja en usage");
    }

    static void TestReallocationPlusieursLibres() throws Exception {
        IDiskManager dm = nouveauDM(nouveauDossier(), 4);
        List<IPageId> ids = allouer(dm, 10);
        Set<IPageId> libres = new HashSet<>(Arrays.asList(ids.get(3), ids.get(5), ids.get(7)));
        for (IPageId id : libres) {
            dm.DeallocPage(id);
        }
        Set<IPageId> reallouees = ensemble(allouer(dm, 3));
        verifierEgal(libres, reallouees, "Les 3 allocations suivantes doivent reprendre les 3 pages libres");
        IPageId nouvelle = dm.AllocPage();
        verifier(!ensemble(ids).contains(nouvelle), "La 4e allocation doit creer une page nouvelle");
    }

    static void TestDeallocToutPuisRealloc() throws Exception {
        IDiskManager dm = nouveauDM(nouveauDossier(), 4);
        List<IPageId> ids = allouer(dm, 20);
        for (IPageId id : ids) {
            dm.DeallocPage(id);
        }
        List<IPageId> reallouees = allouer(dm, 20);
        verifierEgal(ensemble(ids), ensemble(reallouees), "Apres tout desallouer, les 20 pages sont reutilisees");
        verifierEgal(20, ensemble(reallouees).size(), "Aucune page ne doit etre attribuee deux fois");
        IPageId nouvelle = dm.AllocPage();
        verifier(!ensemble(ids).contains(nouvelle), "La 21e allocation doit creer une page nouvelle");
    }

    static void TestReallocPageEcritureFraiche() throws Exception {
        IDiskManager dm = nouveauDM(nouveauDossier(), 4);
        List<IPageId> ids = allouer(dm, 3);
        for (int i = 0; i < 3; i++) {
            ecrire(dm, ids.get(i), motif(4, i));
        }
        dm.DeallocPage(ids.get(1));
        IPageId r = dm.AllocPage();
        verifierEgal(ids.get(1), r, "Reutilisation de la page desallouee");
        ecrire(dm, r, new byte[]{7, 7, 7, 7});
        verifierOctets(new byte[]{7, 7, 7, 7}, lire(dm, r, 4), "Ecriture dans la page reallouee");
        verifierOctets(motif(4, 0), lire(dm, ids.get(0), 4), "Page 0 alteree par la reallocation");
        verifierOctets(motif(4, 2), lire(dm, ids.get(2), 4), "Page 2 alteree par la reallocation");
    }

    static void TestGetActivePageCount() throws Exception {
        IDiskManager dm = nouveauDM(nouveauDossier(), 4);
        verifierEgal(0, dm.GetActivePageCount(), "Compteur apres Init sur dossier vide");
        List<IPageId> ids = allouer(dm, 5);
        verifierEgal(5, dm.GetActivePageCount(), "Compteur apres 5 allocations");
        dm.DeallocPage(ids.get(1));
        dm.DeallocPage(ids.get(3));
        verifierEgal(3, dm.GetActivePageCount(), "Compteur apres 2 desallocations");
        dm.AllocPage();
        verifierEgal(4, dm.GetActivePageCount(), "Compteur apres reutilisation d'une page libre");
        allouer(dm, 3);
        verifierEgal(7, dm.GetActivePageCount(), "Compteur apres 3 allocations supplementaires");
        for (IPageId id : ids) {
            if (!id.equals(ids.get(1)) && !id.equals(ids.get(3))) {
                dm.DeallocPage(id);
            }
        }
        verifierEgal(4, dm.GetActivePageCount(), "Compteur apres desallocation de 3 pages encore actives");
    }

    static void TestGrosVolume() throws Exception {
        int n = 10000;
        Path dossier = nouveauDossier();
        IDiskManager dm = nouveauDM(dossier, 4);
        List<IPageId> ids = allouer(dm, n);
        for (int i = 0; i < n; i++) {
            ecrire(dm, ids.get(i), ByteBuffer.allocate(4).putInt(i).array());
        }
        for (int i = 0; i < n; i++) {
            verifierEgal(i, ByteBuffer.wrap(lire(dm, ids.get(i), 4)).getInt(), "Page " + i + " apres ecriture");
        }
        dm.Save();
        IDiskManager dm2 = nouveauDM(dossier, 4);
        for (int i = 0; i < n; i++) {
            verifierEgal(i, ByteBuffer.wrap(lire(dm2, ids.get(i), 4)).getInt(), "Page " + i + " apres rechargement");
        }
        verifierEgal(n, dm2.GetActivePageCount(), "Compteur apres rechargement de 10000 pages");
    }

    // ------------------------------------------------------------------
    // Tests obligatoires : Save et persistance
    // ------------------------------------------------------------------

    static void TestSaveDossierVide() throws Exception {
        IDiskManager ref = nouveauDM(nouveauDossier(), 4);
        IPageId premiere = ref.AllocPage();
        Path autre = nouveauDossier();
        IDiskManager dm = nouveauDM(autre, 4);
        dm.Save();
        IDiskManager dm2 = nouveauDM(autre, 4);
        verifierEgal(0, dm2.GetActivePageCount(), "Compteur apres Save et Init sur dossier vide");
        verifierEgal(premiere, dm2.AllocPage(), "Apres Save et Init sur dossier vide, on repart de zero");
    }

    static void TestSaveIdempotent() throws Exception {
        Path dossier = nouveauDossier();
        IDiskManager dm = nouveauDM(dossier, 4);
        List<IPageId> ids = allouer(dm, 5);
        for (int i = 0; i < 5; i++) {
            ecrire(dm, ids.get(i), motif(4, i));
        }
        dm.DeallocPage(ids.get(2));
        dm.Save();
        dm.Save();
        dm.Save();
        IDiskManager dm2 = nouveauDM(dossier, 4);
        for (int i = 0; i < 5; i++) {
            if (i != 2) {
                verifierOctets(motif(4, i), lire(dm2, ids.get(i), 4), "Page " + i + " apres Save repetes");
            }
        }
        verifierEgal(ids.get(2), dm2.AllocPage(), "La page libre doit etre presente une seule fois");
        IPageId nouvelle = dm2.AllocPage();
        verifier(!ensemble(ids).contains(nouvelle), "Une seule page libre attendue apres Save repetes");
    }

    static void TestPersistanceContenu() throws Exception {
        Path dossier = nouveauDossier();
        IDiskManager dm = nouveauDM(dossier, 8);
        List<IPageId> ids = allouer(dm, 10);
        for (int i = 0; i < 10; i++) {
            ecrire(dm, ids.get(i), motif(8, i));
        }
        dm.Save();
        IDiskManager dm2 = nouveauDM(dossier, 8);
        for (int i = 0; i < 10; i++) {
            verifierOctets(motif(8, i), lire(dm2, ids.get(i), 8), "Page " + i + " apres rechargement");
        }
    }

    static void TestPersistancePagesLibres() throws Exception {
        Path dossier = nouveauDossier();
        IDiskManager dm = nouveauDM(dossier, 4);
        List<IPageId> ids = allouer(dm, 10);
        Set<IPageId> libres = new HashSet<>(Arrays.asList(ids.get(0), ids.get(4), ids.get(9)));
        for (IPageId id : libres) {
            dm.DeallocPage(id);
        }
        dm.Save();
        IDiskManager dm2 = nouveauDM(dossier, 4);
        Set<IPageId> reallouees = ensemble(allouer(dm2, 3));
        verifierEgal(libres, reallouees, "Les pages libres doivent survivre a Save puis Init");
        IPageId nouvelle = dm2.AllocPage();
        verifier(!ensemble(ids).contains(nouvelle), "Apres les pages libres, une page nouvelle est attendue");
    }

    static void TestPersistanceNouvellesAllocs() throws Exception {
        Path dossier = nouveauDossier();
        IDiskManager dm = nouveauDM(dossier, 4);
        List<IPageId> avant = allouer(dm, 5);
        for (int i = 0; i < 5; i++) {
            ecrire(dm, avant.get(i), motif(4, i));
        }
        dm.Save();
        IDiskManager dm2 = nouveauDM(dossier, 4);
        List<IPageId> apres = allouer(dm2, 5);
        for (IPageId id : apres) {
            verifier(!ensemble(avant).contains(id), "Page " + id + " attribuee deux fois apres rechargement");
        }
        for (int i = 0; i < 5; i++) {
            ecrire(dm2, apres.get(i), motif(4, 100 + i));
        }
        for (int i = 0; i < 5; i++) {
            verifierOctets(motif(4, i), lire(dm2, avant.get(i), 4), "Ancienne page " + i + " ecrasee");
            verifierOctets(motif(4, 100 + i), lire(dm2, apres.get(i), 4), "Nouvelle page " + i);
        }
    }

    static void TestPersistanceCompteurPages() throws Exception {
        Path dossier = nouveauDossier();
        IDiskManager dm = nouveauDM(dossier, 4);
        List<IPageId> ids = allouer(dm, 10);
        dm.DeallocPage(ids.get(2));
        dm.DeallocPage(ids.get(6));
        dm.DeallocPage(ids.get(8));
        dm.Save();
        IDiskManager dm2 = nouveauDM(dossier, 4);
        verifierEgal(7, dm2.GetActivePageCount(), "Compteur de pages actives apres rechargement");
        verifierEgal(4, dm2.GetPageSize(), "Taille de page apres rechargement");
        dm2.AllocPage();
        verifierEgal(8, dm2.GetActivePageCount(), "Compteur apres une allocation suivant le rechargement");
    }

    static void TestPersistanceMultiCycles() throws Exception {
        Path dossier = nouveauDossier();
        Map<IPageId, byte[]> modele = new LinkedHashMap<>();
        for (int cycle = 0; cycle < 15; cycle++) {
            IDiskManager dm = nouveauDM(dossier, 8);
            verifierEgal(modele.size(), dm.GetActivePageCount(), "Cycle " + cycle + ", compteur de pages actives");
            for (Map.Entry<IPageId, byte[]> e : modele.entrySet()) {
                verifierOctets(e.getValue(), lire(dm, e.getKey(), 8), "Cycle " + cycle + ", page " + e.getKey());
            }
            if (cycle % 3 == 2) {
                IPageId aLiberer = modele.keySet().iterator().next();
                modele.remove(aLiberer);
                dm.DeallocPage(aLiberer);
            }
            for (int k = 0; k < 3; k++) {
                IPageId id = dm.AllocPage();
                verifier(!modele.containsKey(id), "Cycle " + cycle + " : page " + id + " deja attribuee");
                byte[] d = motif(8, cycle * 10 + k);
                ecrire(dm, id, d);
                modele.put(id, d);
            }
            dm.Save();
        }
    }

    static void TestDeuxDiskManagersIndependants() throws Exception {
        IDiskManager dm1 = nouveauDM(nouveauDossier(), 4);
        IDiskManager dm2 = nouveauDM(nouveauDossier(), 8);
        List<IPageId> ids1 = new ArrayList<>();
        List<IPageId> ids2 = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids1.add(dm1.AllocPage());
            ids2.add(dm2.AllocPage());
            ecrire(dm1, ids1.get(i), motif(4, i));
            ecrire(dm2, ids2.get(i), motif(8, 50 + i));
        }
        verifierEgal(ids1, ids2, "Deux gestionnaires neufs doivent attribuer les memes identifiants");
        dm1.DeallocPage(ids1.get(2));
        verifierEgal(5, dm2.GetActivePageCount(), "Une desallocation dans dm1 modifie le compteur de dm2");
        verifierEgal(4, dm1.GetPageSize(), "Taille de page de dm1");
        verifierEgal(8, dm2.GetPageSize(), "Taille de page de dm2 (etat statique partage ?)");
        IPageId n2 = dm2.AllocPage();
        verifier(!ensemble(ids2).contains(n2), "Une desallocation dans dm1 affecte dm2 (etat statique partage ?)");
        for (int i = 0; i < 5; i++) {
            verifierOctets(motif(8, 50 + i), lire(dm2, ids2.get(i), 8), "dm2, page " + i);
            if (i != 2) {
                verifierOctets(motif(4, i), lire(dm1, ids1.get(i), 4), "dm1, page " + i);
            }
        }
    }

    // ------------------------------------------------------------------
    // Stress : operations aleatoires comparees a un modele en memoire
    // ------------------------------------------------------------------

    private static void stress(long graine, int taille, int nbOps) throws Exception {
        Random r = new Random(graine);
        Path dossier = nouveauDossier();
        IDiskManager dm = nouveauDM(dossier, taille);
        List<IPageId> vivantes = new ArrayList<>();
        Set<IPageId> vivantesEns = new HashSet<>();
        Set<IPageId> libres = new HashSet<>();
        Set<IPageId> dejaVues = new HashSet<>();
        Map<IPageId, byte[]> modele = new HashMap<>();

        for (int i = 0; i < nbOps; i++) {
            int op = r.nextInt(100);
            if (op < 25 || vivantes.isEmpty()) {
                IPageId id = dm.AllocPage();
                verifier(id != null, "Operation " + i + " : AllocPage renvoie null");
                if (!libres.isEmpty()) {
                    verifier(libres.contains(id),
                            "Operation " + i + " : une page libre existait mais " + id + " a ete renvoyee");
                    libres.remove(id);
                } else {
                    verifier(!dejaVues.contains(id),
                            "Operation " + i + " : page " + id + " deja attribuee et jamais liberee");
                }
                verifier(vivantesEns.add(id), "Operation " + i + " : page " + id + " allouee alors qu'elle est en usage");
                dejaVues.add(id);
                vivantes.add(id);
            } else if (op < 60) {
                IPageId id = vivantes.get(r.nextInt(vivantes.size()));
                byte[] d = new byte[taille];
                r.nextBytes(d);
                ecrire(dm, id, d);
                modele.put(id, d);
            } else if (op < 85) {
                IPageId id = vivantes.get(r.nextInt(vivantes.size()));
                byte[] attendu = modele.get(id);
                if (attendu != null) {
                    verifierOctets(attendu, lire(dm, id, taille), "Operation " + i + " : lecture de " + id);
                }
            } else if (op < 95) {
                int k = r.nextInt(vivantes.size());
                IPageId id = vivantes.get(k);
                vivantes.set(k, vivantes.get(vivantes.size() - 1));
                vivantes.remove(vivantes.size() - 1);
                vivantesEns.remove(id);
                modele.remove(id);
                libres.add(id);
                dm.DeallocPage(id);
            } else {
                dm.Save();
                dm = nouveauDM(dossier, taille);
            }
            verifierEgal(vivantes.size(), dm.GetActivePageCount(), "Operation " + i + " : compteur de pages actives");
        }
        for (Map.Entry<IPageId, byte[]> e : modele.entrySet()) {
            verifierOctets(e.getValue(), lire(dm, e.getKey(), taille), "Verification finale de " + e.getKey());
        }
    }

    // ------------------------------------------------------------------
    // Tests de robustesse (non precises par l'enonce)
    // ------------------------------------------------------------------

    static void TestOperationsAvantInit() throws Exception {
        IDiskManager dm = new DiskManager();
        ByteBuffer bb = ByteBuffer.allocate(4);
        IPageId faux = pageIdArbitraire(0);
        exigerErreur("AllocPage avant Init", dm::AllocPage);
        exigerErreur("Save avant Init", dm::Save);
        if (faux != null) {
            exigerErreur("ReadPage avant Init", () -> dm.ReadPage(faux, bb));
            exigerErreur("WritePage avant Init", () -> dm.WritePage(faux, bb));
            exigerErreur("DeallocPage avant Init", () -> dm.DeallocPage(faux));
        }
    }

    static void TestInitArgumentsInvalides() throws Exception {
        Path dossier = nouveauDossier();
        exigerErreur("Init avec une taille de page de 0", () -> new DiskManager().Init(chemin(dossier), 0));
        exigerErreur("Init avec une taille de page negative", () -> new DiskManager().Init(chemin(dossier), -4));
        exigerErreur("Init avec un dossier null", () -> new DiskManager().Init(null, 4));
        Path fichier = Files.createFile(racine.resolve("un_fichier.txt"));
        exigerErreur("Init avec un chemin qui designe un fichier et non un dossier",
                () -> new DiskManager().Init(chemin(fichier), 4));
    }

    static void TestInitDossierImbrique() throws Exception {
        Path d = racine.resolve("a").resolve("b").resolve("c");
        IDiskManager dm = new DiskManager();
        dm.Init(chemin(d), 4);
        verifier(Files.isDirectory(d), "Init devrait creer aussi les dossiers parents manquants");
        verifier(dm.AllocPage() != null, "AllocPage renvoie null");
    }

    static void TestSecondInitSansEffetDeBord() throws Exception {
        Path a = nouveauDossier();
        Path b = nouveauDossier();
        IDiskManager dm = nouveauDM(a, 4);
        List<IPageId> ids = allouer(dm, 3);
        for (int i = 0; i < 3; i++) {
            ecrire(dm, ids.get(i), motif(4, i));
        }
        exigerErreur("Second Init", () -> dm.Init(chemin(b), 8));
        verifierEgal(4, dm.GetPageSize(), "Un Init refuse ne doit pas changer la taille de page");
        verifierEgal(3, dm.GetActivePageCount(), "Un Init refuse ne doit pas changer l'etat");
        for (int i = 0; i < 3; i++) {
            verifierOctets(motif(4, i), lire(dm, ids.get(i), 4), "Page " + i + " apres un Init refuse");
        }
        IPageId nouvelle = dm.AllocPage();
        verifier(!ensemble(ids).contains(nouvelle), "Un Init refuse a perturbe l'allocation");
    }

    static void TestIdentifiantNull() throws Exception {
        IDiskManager dm = nouveauDM(nouveauDossier(), 4);
        dm.AllocPage();
        ByteBuffer bb = ByteBuffer.allocate(4);
        exigerErreur("ReadPage avec un identifiant null", () -> dm.ReadPage(null, bb));
        exigerErreur("WritePage avec un identifiant null", () -> dm.WritePage(null, bb));
        exigerErreur("DeallocPage avec un identifiant null", () -> dm.DeallocPage(null));
    }

    static void TestIdentifiantEtranger() throws Exception {
        IDiskManager dm = nouveauDM(nouveauDossier(), 4);
        dm.AllocPage();
        IPageId etranger = new IPageId() {
        };
        ByteBuffer bb = ByteBuffer.allocate(4);
        exigerErreur("ReadPage avec un identifiant d'un autre type", () -> dm.ReadPage(etranger, bb));
        exigerErreur("WritePage avec un identifiant d'un autre type", () -> dm.WritePage(etranger, bb));
        exigerErreur("DeallocPage avec un identifiant d'un autre type", () -> dm.DeallocPage(etranger));
    }

    static void TestIdentifiantHorsBornes() throws Exception {
        IDiskManager dm = nouveauDM(nouveauDossier(), 4);
        allouer(dm, 3);
        ByteBuffer bb = ByteBuffer.allocate(4);
        for (int numero : new int[]{3, 1000, -1}) {
            IPageId id = pageIdArbitraire(numero);
            if (id == null) {
                continue;
            }
            exigerErreur("ReadPage sur une page jamais allouee (" + numero + ")", () -> dm.ReadPage(id, bb));
            exigerErreur("WritePage sur une page jamais allouee (" + numero + ")", () -> dm.WritePage(id, bb));
            exigerErreur("DeallocPage sur une page jamais allouee (" + numero + ")", () -> dm.DeallocPage(id));
        }
    }

    static void TestPageLibereeInutilisable() throws Exception {
        IDiskManager dm = nouveauDM(nouveauDossier(), 4);
        List<IPageId> ids = allouer(dm, 3);
        dm.DeallocPage(ids.get(1));
        ByteBuffer bb = ByteBuffer.allocate(4);
        exigerErreur("ReadPage sur une page desallouee", () -> dm.ReadPage(ids.get(1), bb));
        exigerErreur("WritePage sur une page desallouee", () -> dm.WritePage(ids.get(1), bb));
    }

    static void TestDoubleDesallocation() throws Exception {
        IDiskManager dm = nouveauDM(nouveauDossier(), 4);
        List<IPageId> ids = allouer(dm, 2);
        dm.DeallocPage(ids.get(0));
        exigerErreur("Seconde desallocation de la meme page", () -> dm.DeallocPage(ids.get(0)));
        verifierEgal(1, dm.GetActivePageCount(), "Compteur apres une double desallocation");
        IPageId x = dm.AllocPage();
        IPageId y = dm.AllocPage();
        verifier(!x.equals(y), "Apres une double desallocation, la meme page est attribuee deux fois (" + x + ")");
    }

    static void TestBufferInvalide() throws Exception {
        IDiskManager dm = nouveauDM(nouveauDossier(), 8);
        IPageId p = dm.AllocPage();
        exigerErreur("ReadPage avec un buffer null", () -> dm.ReadPage(p, null));
        exigerErreur("WritePage avec un buffer null", () -> dm.WritePage(p, null));
        exigerErreur("ReadPage avec un buffer trop petit", () -> dm.ReadPage(p, ByteBuffer.allocate(4)));
        exigerErreur("WritePage avec un buffer trop petit", () -> dm.WritePage(p, ByteBuffer.allocate(4)));
    }

    static void TestPageFraicheLisibleEtNulle() throws Exception {
        IDiskManager dm = nouveauDM(nouveauDossier(), 8);
        IPageId p = dm.AllocPage();
        byte[] tableau = rempli(8, 0x55);
        dm.ReadPage(p, ByteBuffer.wrap(tableau));
        verifierOctets(new byte[8], tableau, "Une page allouee mais jamais ecrite devrait se lire sans erreur et a zero");
    }

    static void TestBufferDirect() throws Exception {
        IDiskManager dm = nouveauDM(nouveauDossier(), 4);
        IPageId p = dm.AllocPage();
        ByteBuffer bb = ByteBuffer.allocateDirect(4);
        bb.put(new byte[]{1, 2, 3, 4});
        bb.flip();
        dm.WritePage(p, bb);
        ByteBuffer lu = ByteBuffer.allocateDirect(4);
        dm.ReadPage(p, lu);
        byte[] obtenu = new byte[4];
        lu.rewind();
        lu.get(obtenu);
        verifierOctets(new byte[]{1, 2, 3, 4}, obtenu, "Ecriture et lecture avec un ByteBuffer direct");
    }
}
