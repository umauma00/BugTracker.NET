package btnet.runtime.web.ui;

import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.model.IStandaloneElementTag;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.standard.expression.IStandardExpression;
import org.thymeleaf.standard.expression.IStandardExpressionParser;
import org.thymeleaf.standard.expression.StandardExpressions;
import org.thymeleaf.templatemode.TemplateMode;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The Thymeleaf dialect of the port's server controls (RULEBOOK B5): {@code bt:control="${page.x}"} on
 * the template element of server control {@code x}. No .NET counterpart: it stands for each control's
 * {@code Render} applied to the element the template wrote.
 *
 * <p>The processor runs after every Standard-dialect processor of the element (dialect precedence
 * 2000 &gt; 1000), so {@code th:if}/{@code th:each}/{@code th:attr} have already applied. It then:
 * <ul>
 * <li>removes the element when the control is not {@code Visible} (or renders nothing, e.g. an
 *     unbound DataGrid);</li>
 * <li>sets every attribute the control owns ({@code id} = {@code ClientID}, {@code name} =
 *     {@code UniqueID}/group, {@code value}, {@code checked}, {@code disabled}, {@code class},
 *     {@code style}, {@code onchange} with the AutoPostBack call, {@code Attributes}…), HTML
 *     attribute-encoded, and removes those the control says must be absent; literal template
 *     attributes the control does not own stay;</li>
 * <li>replaces the element body with the control's inner HTML when it has one ({@code InnerHtml}/
 *     {@code InnerText}, {@code Label.Text}, select options, list items, DataGrid rows); otherwise the
 *     template's body stays.</li>
 * </ul>
 * Register one instance as a Spring bean ({@code BtnetApplication}): Boot adds every {@code IDialect}
 * bean to the template engine.
 */
public class BtnetDialect extends AbstractProcessorDialect {
    public static final String NAME = "BugTracker.NET server controls";
    public static final String PREFIX = "bt";
    /** Higher than {@code StandardDialect.PROCESSOR_PRECEDENCE} (1000): runs last on an element. */
    public static final int PRECEDENCE = 2000;

    public BtnetDialect() {
        super(NAME, PREFIX, PRECEDENCE);
    }

    @Override
    public Set<IProcessor> getProcessors(String dialectPrefix) {
        Set<IProcessor> s = new LinkedHashSet<>();
        s.add(new ControlAttributeProcessor(dialectPrefix));
        return s;
    }

    /** {@code bt:control}. */
    public static final class ControlAttributeProcessor extends AbstractAttributeTagProcessor {
        public static final String ATTR_NAME = "control";

        public ControlAttributeProcessor(String dialectPrefix) {
            super(TemplateMode.HTML, dialectPrefix, null, false, ATTR_NAME, true, 1000, true);
        }

        @Override
        protected void doProcess(ITemplateContext context, IProcessableElementTag tag, AttributeName attributeName,
                                 String attributeValue, IElementTagStructureHandler structureHandler) {
            IStandardExpressionParser parser = StandardExpressions.getExpressionParser(context.getConfiguration());
            IStandardExpression expression = parser.parseExpression(context, attributeValue);
            Object value = expression.execute(context);
            if (!(value instanceof Control<?> control)) {
                throw new TemplateProcessingException("bt:control=\"" + attributeValue + "\" did not evaluate to a server control (got "
                        + (value == null ? "null" : value.getClass().getName()) + ")");
            }
            apply(control, tag, structureHandler);
        }

        static void apply(Control<?> control, IProcessableElementTag tag, IElementTagStructureHandler sh) {
            if (!control.Visible() || control.IsRenderSuppressed()) {
                sh.removeElement();
                return;
            }
            Map<String, String> attrs = control.attributesForTemplate();
            for (Map.Entry<String, String> e : attrs.entrySet()) {
                if (e.getValue() == null) {
                    if (tag.hasAttribute(e.getKey())) sh.removeAttribute(e.getKey());
                } else {
                    sh.setAttribute(e.getKey(), Html.attr(e.getValue()));
                }
            }
            if (!(tag instanceof IStandaloneElementTag)) {
                String body = control.innerHtmlForTemplate(tag.getElementCompleteName());
                if (body != null) sh.setBody(body, false);
            }
        }
    }
}
