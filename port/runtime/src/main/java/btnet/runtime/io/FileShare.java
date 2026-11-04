package btnet.runtime.io;

/** Stands for .NET {@code System.IO.FileShare}; accepted and ignored by {@link FileStream} [J-C6]. */
public enum FileShare {
    None, Read, Write, ReadWrite, Delete, Inheritable
}
