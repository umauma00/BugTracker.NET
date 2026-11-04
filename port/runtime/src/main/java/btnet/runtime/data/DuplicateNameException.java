package btnet.runtime.data;

import btnet.runtime.DotNetException;

/** .NET {@code System.Data.DuplicateNameException} (RULEBOOK §3 [S1-19]). */
public class DuplicateNameException extends DotNetException {
    private static final long serialVersionUID = 1L;

    public DuplicateNameException(String message) {
        super(message);
    }
}
