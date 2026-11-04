package btnet.runtime.io;

import btnet.runtime.DotNetException;
import btnet.runtime.Exceptions;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Stands for .NET {@code System.Xml.XmlTextReader} (RULEBOOK §2A, §3 C35/J-C4) over StAX {@link XMLStreamReader},
 * <b>opened lazily</b>: the constructor stores the path; the first {@link #Read()} opens the file (missing →
 * FileNotFoundException("Could not find file '&lt;path&gt;'.")). Node sequence as .NET with
 * {@code WhitespaceHandling.All}: an empty element {@code <a/>} is one {@code Element} node (no
 * {@code EndElement}); a declaration is an {@code XmlDeclaration} node named {@code xml}.
 * Malformed XML → {@link DotNetException} (.NET XmlException; not otherwise modelled).
 */
public class XmlTextReader implements AutoCloseable {
    private final String path;
    private XMLStreamReader xr;
    private String text;
    private boolean opened;
    private boolean closed;
    private boolean eof;
    private boolean declarationPending;
    private boolean skipNextEnd;

    private XmlNodeType nodeType = XmlNodeType.None;
    private String name = "";
    private String value = "";
    private boolean isEmpty;
    private Map<String, String> attributes = new LinkedHashMap<>();

    public XmlTextReader(String path) {
        if (path == null) {
            throw new btnet.runtime.ArgumentNullException("url");
        }
        this.path = path;
    }

    private void open() {
        java.io.File f = File.file(path);
        File.requireFile(f);
        byte[] bytes;
        try {
            bytes = java.nio.file.Files.readAllBytes(f.toPath());
        } catch (java.io.IOException e) {
            throw Exceptions.wrap(e);
        }
        text = decode(bytes);
        try {
            XMLInputFactory fac = XMLInputFactory.newFactory();
            fac.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            fac.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            fac.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.FALSE);
            fac.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
            xr = fac.createXMLStreamReader(new StringReader(text));
            declarationPending = text.length() > 5 && text.startsWith("<?xml")
                    && Character.isWhitespace(text.charAt(5));
        } catch (XMLStreamException e) {
            throw new DotNetException(e.getMessage(), e);
        }
        opened = true;
    }

    private static final Pattern ENC = Pattern.compile("^<\\?xml[^>]*encoding\\s*=\\s*[\"']([A-Za-z0-9._-]+)[\"']");

    private static String decode(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            return new String(b, 3, b.length - 3, StandardCharsets.UTF_8);
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16LE);
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16BE);
        }
        String head = new String(b, 0, Math.min(b.length, 200), StandardCharsets.ISO_8859_1);
        Matcher m = ENC.matcher(head);
        Charset cs = StandardCharsets.UTF_8;
        if (m.find()) {
            try {
                cs = Charset.forName(m.group(1));
            } catch (RuntimeException e) {
                cs = StandardCharsets.UTF_8;
            }
        }
        return new String(b, cs);
    }

    /** .NET {@code Read()}: advances to the next node; false at end of document. */
    public boolean Read() {
        if (closed || eof) {
            return false;
        }
        if (!opened) {
            open();
        }
        if (declarationPending) {
            declarationPending = false;
            nodeType = XmlNodeType.XmlDeclaration;
            name = "xml";
            StringBuilder v = new StringBuilder("version=\"").append(xr.getVersion() == null ? "1.0" : xr.getVersion()).append('"');
            if (xr.getCharacterEncodingScheme() != null) {
                v.append(" encoding=\"").append(xr.getCharacterEncodingScheme()).append('"');
            }
            if (xr.standaloneSet()) {
                v.append(" standalone=\"").append(xr.isStandalone() ? "yes" : "no").append('"');
            }
            value = v.toString();
            isEmpty = false;
            attributes = new LinkedHashMap<>();
            return true;
        }
        try {
            while (true) {
                if (!xr.hasNext()) {
                    return end();
                }
                int ev = xr.next();
                attributes = new LinkedHashMap<>();
                isEmpty = false;
                value = "";
                name = "";
                switch (ev) {
                    case XMLStreamConstants.START_ELEMENT: {
                        nodeType = XmlNodeType.Element;
                        name = qname(xr.getPrefix(), xr.getLocalName());
                        for (int i = 0; i < xr.getAttributeCount(); i++) {
                            attributes.put(qname(xr.getAttributePrefix(i), xr.getAttributeLocalName(i)),
                                    xr.getAttributeValue(i));
                        }
                        int off = xr.getLocation().getCharacterOffset();
                        if (off >= 2 && off <= text.length() && text.charAt(off - 1) == '>'
                                && text.charAt(off - 2) == '/') {
                            isEmpty = true;
                            skipNextEnd = true;
                        }
                        return true;
                    }
                    case XMLStreamConstants.END_ELEMENT:
                        if (skipNextEnd) {
                            skipNextEnd = false;
                            continue;
                        }
                        nodeType = XmlNodeType.EndElement;
                        name = qname(xr.getPrefix(), xr.getLocalName());
                        return true;
                    case XMLStreamConstants.CHARACTERS:
                        value = xr.getText();
                        nodeType = xr.isWhiteSpace() ? XmlNodeType.Whitespace : XmlNodeType.Text;
                        return true;
                    case XMLStreamConstants.SPACE:
                        value = xr.getText();
                        nodeType = XmlNodeType.Whitespace;
                        return true;
                    case XMLStreamConstants.CDATA:
                        value = xr.getText();
                        nodeType = XmlNodeType.CDATA;
                        return true;
                    case XMLStreamConstants.COMMENT:
                        value = xr.getText();
                        nodeType = XmlNodeType.Comment;
                        return true;
                    case XMLStreamConstants.PROCESSING_INSTRUCTION:
                        name = xr.getPITarget();
                        value = xr.getPIData() == null ? "" : xr.getPIData();
                        nodeType = XmlNodeType.ProcessingInstruction;
                        return true;
                    case XMLStreamConstants.DTD:
                        nodeType = XmlNodeType.DocumentType;
                        value = xr.getText();
                        return true;
                    case XMLStreamConstants.ENTITY_REFERENCE:
                        nodeType = XmlNodeType.EntityReference;
                        name = xr.getLocalName();
                        return true;
                    case XMLStreamConstants.END_DOCUMENT:
                        return end();
                    default:
                        continue;
                }
            }
        } catch (XMLStreamException e) {
            throw new DotNetException(e.getMessage(), e);
        }
    }

    private boolean end() {
        eof = true;
        nodeType = XmlNodeType.None;
        name = "";
        value = "";
        attributes = new LinkedHashMap<>();
        return false;
    }

    private static String qname(String prefix, String local) {
        return prefix == null || prefix.isEmpty() ? local : prefix + ":" + local;
    }

    /** .NET {@code NodeType}. */
    public XmlNodeType NodeType() {
        return nodeType;
    }

    /** .NET {@code Name} (qualified name; {@code ""} for text, comments, white space). */
    public String Name() {
        return name;
    }

    /** .NET {@code LocalName}. */
    public String LocalName() {
        int i = name.indexOf(':');
        return i < 0 ? name : name.substring(i + 1);
    }

    /** .NET {@code Value}. */
    public String Value() {
        return value;
    }

    /** .NET {@code IsEmptyElement}. */
    public boolean IsEmptyElement() {
        return isEmpty;
    }

    /** .NET {@code HasAttributes}. */
    public boolean HasAttributes() {
        return !attributes.isEmpty();
    }

    /** .NET {@code AttributeCount}. */
    public int AttributeCount() {
        return attributes.size();
    }

    /** .NET {@code GetAttribute(name)}: on the current element; {@code null} if absent. */
    public String GetAttribute(String attr) {
        return attributes.get(attr);
    }

    /** .NET indexer {@code tr["value"]} [J-C4] = {@link #GetAttribute}. */
    public String get(String attr) {
        return GetAttribute(attr);
    }

    /** .NET {@code EOF}. */
    public boolean EOF() {
        return eof;
    }

    /** .NET {@code Close()}: idempotent. */
    public void Close() {
        if (!closed) {
            closed = true;
            nodeType = XmlNodeType.None;
            if (xr != null) {
                try {
                    xr.close();
                } catch (XMLStreamException e) {
                    // closing never fails in .NET
                }
            }
        }
    }

    @Override
    public void close() {
        Close();
    }
}
