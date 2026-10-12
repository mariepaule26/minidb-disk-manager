/**
 * Identifiant d'une page : son numero dans l'unique fichier de donnees.
 * La page n se trouve a l'offset n * pageSize.
 */
public class PageId implements IPageId {

    private final int pageIdx;

    public PageId(int pageIdx) {
        this.pageIdx = pageIdx;
    }

    public int getPageIdx() {
        return pageIdx;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PageId)) {
            return false;
        }
        return pageIdx == ((PageId) obj).pageIdx;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(pageIdx);
    }

    @Override
    public String toString() {
        return "PageId(" + pageIdx + ")";
    }
}
