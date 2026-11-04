package btnet.runtime.web.ui;

import java.util.List;

/** Internal {@code RepeatInfo.RenderRepeater} for {@link CheckBoxList}/{@link RadioButtonList}. */
final class Repeat {
    private Repeat() {}

    static String layout(List<String> cells, boolean table, boolean horizontal) {
        StringBuilder sb = new StringBuilder();
        if (table) {
            if (horizontal) {
                sb.append("\r\n\t<tr>\r\n");
                for (String c : cells) sb.append("\t\t<td>").append(c).append("</td>");
                sb.append("\r\n\t</tr>\r\n");
            } else {
                for (String c : cells) sb.append("\r\n\t<tr>\r\n\t\t<td>").append(c).append("</td>\r\n\t</tr>");
                if (!cells.isEmpty()) sb.append("\r\n");
            }
        } else {
            for (int i = 0; i < cells.size(); i++) {
                if (i > 0 && !horizontal) sb.append("<br />");
                sb.append(cells.get(i));
            }
        }
        return sb.toString();
    }
}
