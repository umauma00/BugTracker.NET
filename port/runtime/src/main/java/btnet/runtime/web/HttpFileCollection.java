package btnet.runtime.web;

import java.util.ArrayList;
import java.util.List;

/**
 * .NET {@code System.Web.HttpFileCollection} ({@code Request.Files}): every file part of
 * a multipart request, in posted order, including file inputs left empty
 * ({@code FileName() == ""}, {@code ContentLength() == 0}). {@code get(name)} is
 * case-insensitive and returns {@code null} when absent; {@code get(int)} throws when out
 * of range ({@code ArgumentOutOfRangeException} in .NET → {@code IndexOutOfBoundsException}).
 */
public final class HttpFileCollection {
    private final List<String> names = new ArrayList<>();
    private final List<HttpPostedFile> files = new ArrayList<>();

    HttpFileCollection() {
    }

    void add(String name, HttpPostedFile file) {
        names.add(name);
        files.add(file);
    }

    public HttpPostedFile get(String name) {
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            if (n != null && name != null && HttpApplicationState.fold(n).equals(HttpApplicationState.fold(name))) {
                return files.get(i);
            }
        }
        return null;
    }

    public HttpPostedFile get(int index) {
        return files.get(index);
    }

    public int Count() {
        return files.size();
    }

    public String[] AllKeys() {
        return names.toArray(new String[0]);
    }
}
