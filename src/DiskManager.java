import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Gestionnaire de l'espace disque.
 *
 * Fonctionnement :
 *  - toutes les pages sont stockees dans un seul fichier, Data.bin
 *  - la page numero n commence a l'octet n * pageSize de ce fichier
 *  - le fichier dm.save garde l'etat du gestionnaire entre deux executions :
 *    taille de page, nombre de pages creees et liste des pages liberees
 *  - toutes les erreurs sont signalees par une DiskManagerException
 */
public class DiskManager implements IDiskManager {

    private static final String NOM_FICHIER_DONNEES = "Data.bin";
    private static final String NOM_FICHIER_SAUVEGARDE = "dm.save";

    // vrai des que Init a reussi (Init ne peut etre appele qu'une fois)
    private boolean initialise;

    private File dossier;
    private int pageSize;

    // nombre de pages creees dans Data.bin, libres ou non
    private int nbPages;

    // numeros des pages desallouees, qu'on peut reutiliser
    private ArrayList<Integer> pagesLibres;

    private RandomAccessFile fichierDonnees;

    public DiskManager() {
        this.initialise = false;
        this.pagesLibres = new ArrayList<Integer>();
    }

    @Override
    public void Init(String dmDir, int pageSize) throws DiskManagerException {
        if (initialise) {
            throw new DiskManagerException("Init a deja ete appele sur ce DiskManager");
        }
        if (dmDir == null) {
            throw new DiskManagerException("Le dossier de travail est null");
        }
        if (pageSize <= 0) {
            throw new DiskManagerException("La taille de page doit etre positive : " + pageSize);
        }

        File dir = new File(dmDir);
        if (dir.exists() && !dir.isDirectory()) {
            throw new DiskManagerException(dmDir + " existe mais n'est pas un dossier");
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new DiskManagerException("Impossible de creer le dossier " + dmDir);
        }

        File fichierSauvegarde = new File(dir, NOM_FICHIER_SAUVEGARDE);
        int nbPagesLues = 0;
        ArrayList<Integer> libresLues = new ArrayList<Integer>();

        // Si le dossier contient deja des donnees, on les relit.
        // On le fait avant d'ouvrir Data.bin : si la taille de page est incompatible,
        // on sort en erreur sans avoir touche a rien.
        if (fichierSauvegarde.exists()) {
            try (DataInputStream in = new DataInputStream(new FileInputStream(fichierSauvegarde))) {
                int tailleSauvee = in.readInt();
                if (tailleSauvee != pageSize) {
                    throw new DiskManagerException("Taille de page incompatible : le dossier contient des pages de "
                            + tailleSauvee + " octets, mais Init a recu " + pageSize);
                }
                nbPagesLues = in.readInt();
                int nbLibres = in.readInt();
                if (nbPagesLues < 0 || nbLibres < 0 || nbLibres > nbPagesLues) {
                    throw new DiskManagerException("Fichier de sauvegarde corrompu");
                }
                for (int i = 0; i < nbLibres; i++) {
                    int numero = in.readInt();
                    if (numero < 0 || numero >= nbPagesLues) {
                        throw new DiskManagerException("Fichier de sauvegarde corrompu : page libre " + numero);
                    }
                    libresLues.add(numero);
                }
            } catch (IOException e) {
                throw new DiskManagerException("Impossible de lire " + fichierSauvegarde, e);
            }
        }

        try {
            RandomAccessFile raf = new RandomAccessFile(new File(dir, NOM_FICHIER_DONNEES), "rw");
            if (!fichierSauvegarde.exists()) {
                // dossier sans sauvegarde : on repart de zero, donc on vide d'eventuelles vieilles donnees
                raf.setLength(0);
            }
            this.fichierDonnees = raf;
        } catch (IOException e) {
            throw new DiskManagerException("Impossible d'ouvrir " + NOM_FICHIER_DONNEES, e);
        }

        this.dossier = dir;
        this.pageSize = pageSize;
        this.nbPages = nbPagesLues;
        this.pagesLibres = libresLues;
        this.initialise = true;
    }

    @Override
    public void Save() throws DiskManagerException {
        verifierInit();
        File fichierSauvegarde = new File(dossier, NOM_FICHIER_SAUVEGARDE);
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(fichierSauvegarde))) {
            out.writeInt(pageSize);
            out.writeInt(nbPages);
            out.writeInt(pagesLibres.size());
            for (int numero : pagesLibres) {
                out.writeInt(numero);
            }
        } catch (IOException e) {
            throw new DiskManagerException("Impossible d'ecrire " + fichierSauvegarde, e);
        }
    }

    @Override
    public IPageId AllocPage() throws DiskManagerException {
        verifierInit();
        if (!pagesLibres.isEmpty()) {
            // on reutilise la derniere page liberee
            int numero = pagesLibres.remove(pagesLibres.size() - 1);
            return new PageId(numero);
        }
        // sinon on ajoute une page a la fin
        PageId nouvelle = new PageId(nbPages);
        nbPages++;
        return nouvelle;
    }

    @Override
    public void ReadPage(IPageId ipid, ByteBuffer buffer) throws DiskManagerException {
        int numero = numeroPage(ipid);
        verifierBuffer(buffer);

        byte[] page = new byte[pageSize];
        try {
            fichierDonnees.seek((long) numero * pageSize);
            int nbLus = 0;
            while (nbLus < pageSize) {
                int n = fichierDonnees.read(page, nbLus, pageSize - nbLus);
                if (n == -1) {
                    // fin du fichier : cette partie de la page n'a jamais ete ecrite, elle reste a 0
                    break;
                }
                nbLus += n;
            }
        } catch (IOException e) {
            throw new DiskManagerException("Erreur de lecture de la page " + numero, e);
        }
        buffer.put(page);
    }

    @Override
    public void WritePage(IPageId ipid, ByteBuffer buffer) throws DiskManagerException {
        int numero = numeroPage(ipid);
        verifierBuffer(buffer);

        byte[] page = new byte[pageSize];
        // duplicate() pour ne pas deplacer la position du buffer de l'appelant
        buffer.duplicate().get(page);
        try {
            fichierDonnees.seek((long) numero * pageSize);
            fichierDonnees.write(page);
        } catch (IOException e) {
            throw new DiskManagerException("Erreur d'ecriture de la page " + numero, e);
        }
    }

    @Override
    public void DeallocPage(IPageId ipid) throws DiskManagerException {
        int numero = numeroPage(ipid);
        pagesLibres.add(numero);
    }

    @Override
    public int GetActivePageCount() {
        return nbPages - pagesLibres.size();
    }

    @Override
    public int GetPageSize() {
        return pageSize;
    }

    // ------------------------------------------------------------------
    // Methodes privees de verification
    // ------------------------------------------------------------------

    private void verifierInit() throws DiskManagerException {
        if (!initialise) {
            throw new DiskManagerException("Init n'a pas encore ete appele");
        }
    }

    /**
     * Verifie que l'identifiant designe une page allouee et renvoie son numero.
     */
    private int numeroPage(IPageId ipid) throws DiskManagerException {
        verifierInit();
        if (ipid == null) {
            throw new DiskManagerException("Identifiant de page null");
        }
        if (!(ipid instanceof PageId)) {
            throw new DiskManagerException("Identifiant de page d'un type inconnu");
        }
        int numero = ((PageId) ipid).getPageIdx();
        if (numero < 0 || numero >= nbPages) {
            throw new DiskManagerException("La page " + numero + " n'existe pas");
        }
        if (pagesLibres.contains(numero)) {
            throw new DiskManagerException("La page " + numero + " n'est pas allouee");
        }
        return numero;
    }

    private void verifierBuffer(ByteBuffer buffer) throws DiskManagerException {
        if (buffer == null) {
            throw new DiskManagerException("Buffer null");
        }
        if (buffer.remaining() < pageSize) {
            throw new DiskManagerException("Buffer trop petit : " + buffer.remaining()
                    + " octets disponibles, " + pageSize + " necessaires");
        }
    }
}
