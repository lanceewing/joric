package emu.joric;

import com.badlogic.gdx.Preferences;

import emu.joric.config.AppConfigItem;

/**
 * Configuration of available ROM options for JOric.
 *
 * The user can select which ROM the emulator loads via a settings dialog.
 * Selection is persisted in JOric's Preferences under KEY_SELECTED_ROM.
 *
 * To add a new ROM option:
 *   1. Place the .rom file in assets/roms/
 *   2. Add a new entry to OPTIONS below
 */
public final class RomConfig {

    /** A single ROM choice exposed to the user. */
    public static final class Option {
        public final String id;        // stable identifier stored in preferences
        public final String displayName;
        public final String filename;  // filename in assets/roms/

        public Option(String id, String displayName, String filename) {
            this.id = id;
            this.displayName = displayName;
            this.filename = filename;
        }
    }

    /** Available ROM options. The first entry is the default. */
    public static final Option[] OPTIONS = {
        new Option("atmos", "Oric Atmos (BASIC 1.1)", "basic11b.rom"),
        new Option("oric1", "Oric-1 (BASIC 1.0)",     "basic10.rom"),
    };

    /** Preference key under which the selected ROM id is stored. */
    public static final String KEY_SELECTED_ROM = "selected_rom";

    /**
     * ROM id captured from the page's {@code ?rom=} URL parameter. Populated
     * by the GWT platform at page load when the URL is launching a specific
     * program. Cleared by {@link #takeUrlRomParam()} when consumed, so only
     * the launch that originated with the URL uses it — subsequent launches
     * fall through to the normal resolution path.
     */
    private static String urlRomParam;

    private RomConfig() {}

    /**
     * Returns the currently selected ROM Option, falling back to the default
     * (first option) if no preference is set or the stored id is unknown.
     */
    public static Option getSelected(Preferences preferences) {
        String id = preferences != null ? preferences.getString(KEY_SELECTED_ROM, null) : null;
        if (id != null) {
            for (Option opt : OPTIONS) {
                if (opt.id.equals(id)) return opt;
            }
        }
        return OPTIONS[0];
    }

    /**
     * Save the selected ROM id to preferences. Pass an Option.id (not displayName).
     */
    public static void setSelected(Preferences preferences, String id) {
        if (preferences == null) return;
        preferences.putString(KEY_SELECTED_ROM, id);
        preferences.flush();
    }

    /**
     * Stores the ROM id supplied via the page's {@code ?rom=} URL parameter,
     * to be applied to the next program launched. Silently ignores ids that
     * don't match any known option, so callers can safely pass unvalidated
     * input straight from the URL.
     */
    public static void setUrlRomParam(String id) {
        if (id == null) return;
        // Be forgiving about common URL typos — trim whitespace and trailing
        // slashes (e.g. "?rom=oric1/#/basic" where the user put the slash
        // before the # by analogy with path URLs).
        id = id.trim();
        while (id.endsWith("/")) {
            id = id.substring(0, id.length() - 1);
        }
        if (id.isEmpty()) return;
        for (Option opt : OPTIONS) {
            if (opt.id.equals(id)) {
                urlRomParam = id;
                return;
            }
        }
        // Unknown id — ignore.
    }

    /**
     * Returns the pending URL {@code ?rom=} Option and clears the field, so
     * the value is applied at most once. Returns null if no URL ROM parameter
     * was captured, or if it has already been taken.
     */
    public static Option takeUrlRomParam() {
        if (urlRomParam == null) return null;
        String id = urlRomParam;
        urlRomParam = null;
        for (Option opt : OPTIONS) {
            if (opt.id.equals(id)) return opt;
        }
        return null;
    }

    /**
     * Returns the Option whose id matches the given id, or null if no such
     * option exists (or id is null/empty).
     */
    public static Option findById(String id) {
        if (id == null || id.isEmpty()) return null;
        for (Option opt : OPTIONS) {
            if (opt.id.equals(id)) return opt;
        }
        return null;
    }

    /**
     * Resolves which ROM to use for launching a given program.
     * <p>
     * There are three distinct sources, each with a clearly-defined scope:
     * <ul>
     *   <li><b>URL {@code ?rom=} parameter</b> — applies to the program being
     *       launched from this URL. Highest priority, so sharing a URL gives
     *       the recipient the same ROM the sender intended regardless of
     *       their local settings.</li>
     *   <li><b>{@code AppConfigItem.rom}</b> — the curator's ROM declaration
     *       for a specific entry in programs.json. Applies to curated
     *       launches (home screen tiles) and to URL launches that don't
     *       carry their own {@code ?rom=}.</li>
     *   <li><b>Persisted preference</b> (Settings dialog) — the user's ROM
     *       choice for local-file loads (drag-drop and file picker).</li>
     * </ul>
     * A single launch uses at most one of the above, in that order of
     * priority. If none apply, the Atmos default is used.
     * <p>
     * Local-file launches are distinguished by having a non-empty, non-http
     * filePath. Everything else (curated tiles including BASIC, and URL
     * launches via {@code ?url=} or a {@code #/slug} route) is treated as
     * curator-or-default territory — the persisted preference is not
     * consulted.
     */
    public static Option resolveRom(AppConfigItem appConfigItem, Preferences preferences) {
        Option urlRom = takeUrlRomParam();
        if (urlRom != null) return urlRom;

        String filePath = appConfigItem != null ? appConfigItem.getFilePath() : null;
        boolean localFile = filePath != null && !filePath.isEmpty() && !filePath.startsWith("http");
        if (localFile) {
            return getSelected(preferences);
        }
        Option curatorRom = findById(appConfigItem != null ? appConfigItem.getRom() : null);
        if (curatorRom != null) return curatorRom;
        return OPTIONS[0]; // Atmos default
    }
}
