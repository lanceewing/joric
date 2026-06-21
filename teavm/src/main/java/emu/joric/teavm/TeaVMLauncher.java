package emu.joric.teavm;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import com.github.xpenatan.gdx.teavm.backends.web.utils.WebBaseUrlProvider;

import emu.joric.JOric;
import emu.joric.RomConfig;

public class TeaVMLauncher {

    private static JOric joric;

    public static void main(String[] args) {
        WebApplicationConfiguration config = new WebApplicationConfiguration();
        config.width = 0;
        config.height = 0;
        config.usePhysicalPixels = true;
        config.showDownloadLogs = true;
        config.baseUrlProvider = createBaseUrlProvider();

        Map<String, String> argsMap = createArgsMap();
        TeaVMJOricRunner joricRunner = new TeaVMJOricRunner();
        TeaVMDialogHandler dialogHandler = new TeaVMDialogHandler();
        joric = new JOric(joricRunner, dialogHandler, argsMap);
        registerFileDropHandler();
        new WebApplication(joric, config);
    }

    private static WebBaseUrlProvider createBaseUrlProvider() {
        return () -> {
            String href = TeaVMBrowser.getHref();
            if ((href == null) || href.isEmpty()) {
                return "";
            }

            int queryIndex = href.indexOf('?');
            if (queryIndex >= 0) {
                href = href.substring(0, queryIndex);
            }

            int hashIndex = href.indexOf('#');
            if (hashIndex >= 0) {
                href = href.substring(0, hashIndex);
            }

            if (href.endsWith("/index.html")) {
                return href.substring(0, href.length() - "index.html".length());
            }

            if (!href.endsWith("/")) {
                int lastSlashIndex = href.lastIndexOf('/');
                if (lastSlashIndex >= 0) {
                    return href.substring(0, lastSlashIndex + 1);
                }
                return href + "/";
            }

            return href;
        };
    }

    private static void registerFileDropHandler() {
        TeaVMBrowser.registerFileDropHandler((success, fileName, binaryData) -> {
            if (!success || (joric == null) || (fileName == null) || (binaryData == null)) {
                return;
            }

            byte[] fileData = convertBinaryStringToBytes(binaryData);
            if (Gdx.app != null) {
                Gdx.app.postRunnable(() -> joric.fileDropped(fileName, fileData));
            } else {
                joric.fileDropped(fileName, fileData);
            }
        });
    }

    private static byte[] convertBinaryStringToBytes(String binaryStr) {
        byte[] bytes = new byte[binaryStr.length()];
        for (int i = 0; i < binaryStr.length(); i++) {
            bytes[i] = (byte)(binaryStr.charAt(i) & 0xFF);
        }
        return bytes;
    }

    private static Map<String, String> createArgsMap() {
        Map<String, String> argsMap = new HashMap<>();

        String urlPath = TeaVMBrowser.getPath();
        if ("/".equals(urlPath) || "".equals(urlPath)) {
            String hash = TeaVMBrowser.getHash();
            if ((hash != null) && !hash.isEmpty()) {
                hash = hash.toLowerCase();
                if (hash.startsWith("#/")) {
                    String programId = hash.substring(2);
                    String queryString = "";
                    int questionMarkIndex = programId.indexOf('?');
                    if (questionMarkIndex > 1) {
                        queryString = programId.substring(questionMarkIndex + 1);
                        programId = programId.substring(0, questionMarkIndex);
                        applyQueryString(queryString, argsMap);
                    }
                    if (programId.indexOf('/') >= 0) {
                        String[] hashParts = programId.split("/");
                        argsMap.put("uri", hashParts[0]);
                        if (hashParts.length > 1) {
                            applyPathParts(hashParts, argsMap);
                        }
                    } else {
                        argsMap.put("uri", programId);
                    }
                }
            } else {
                String programUrl = TeaVMBrowser.getQueryParameter("url");
                if ((programUrl != null) && !programUrl.trim().isEmpty()) {
                    if (TeaVMBrowser.isValidUrl(programUrl) && !programUrl.toLowerCase().endsWith(".tgz")) {
                        argsMap.put("url", programUrl);
                    } else {
                        TeaVMBrowser.replaceState(TeaVMBrowser.buildCleanUrl());
                    }
                }
            }

            applyRequestParameters(argsMap);
        }

        // Capture rom= URL parameter when a direct-load URL is present.
        String romParam = TeaVMBrowser.getQueryParameter("rom");
        if ((romParam != null) && !romParam.isEmpty()) {
            String urlParam = TeaVMBrowser.getQueryParameter("url");
            String hash = TeaVMBrowser.getHash();
            boolean directLoad = ((urlParam != null) && !urlParam.isEmpty())
                    || ((hash != null) && !hash.trim().isEmpty());
            if (directLoad) {
                RomConfig.setUrlRomParam(romParam);
            }
        }

        return argsMap;
    }

    private static void applyPathParts(String[] pathParts, Map<String, String> argsMap) {
        if ((pathParts != null) && (pathParts.length > 1)) {
            for (int i = 1; i < pathParts.length; i++) {
                String pathPart = pathParts[i].toUpperCase();
                switch (pathPart) {
                    case "RAM_48K":
                    case "RAM_16K":
                        argsMap.put("ram", pathPart);
                        break;
                    case "PAL":
                    case "NTSC":
                        argsMap.put("tv", pathPart);
                        break;
                    case "TAPE":
                    case "DISK":
                    case "ROM":
                        argsMap.put("type", pathPart);
                        break;
                    case "ATMOS":
                    case "ORIC1":
                        argsMap.put("rom", pathPart);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private static void applyQueryString(String queryString, Map<String, String> argsMap) {
        if ((queryString != null) && !queryString.trim().isEmpty()) {
            String[] queryParams = queryString.trim().split("[&]");
            for (String queryParam : queryParams) {
                int equalsIndex = queryParam.indexOf('=');
                if ((equalsIndex > 0) && (equalsIndex < (queryParam.length() - 1))) {
                    String paramName = queryParam.substring(0, equalsIndex).trim();
                    String paramValue = queryParam.substring(equalsIndex + 1).trim();
                    if (!paramName.isEmpty() && !paramValue.isEmpty()) {
                        argsMap.put(paramName, paramValue);
                    }
                }
            }
        }
    }

    private static void applyRequestParameters(Map<String, String> argsMap) {
        mapParameterIfPresent("ram", argsMap);
        mapParameterIfPresent("tv", argsMap);
        mapParameterIfPresent("type", argsMap);
        mapParameterIfPresent("rom", argsMap);
    }

    private static void mapParameterIfPresent(String paramName, Map<String, String> argsMap) {
        String paramValue = TeaVMBrowser.getQueryParameter(paramName);
        if ((paramValue != null) && !paramValue.trim().isEmpty()) {
            argsMap.put(paramName, paramValue);
        }
    }
}
