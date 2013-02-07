package org.nuxeo.ecm.plaform.rendering.xwiki;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.util.Map;

import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.platform.rendering.api.RenderingException;
import org.nuxeo.ecm.platform.rendering.fm.FreemarkerEngine;
import org.nuxeo.ecm.platform.rendering.fm.adapters.ComplexPropertyTemplate;
import org.xwiki.component.embed.EmbeddableComponentManager;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.transformation.Transformation;
import org.xwiki.rendering.transformation.TransformationContext;

import freemarker.core.Environment;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;


public class XWikiTransformer implements TemplateDirectiveModel {

    private static EmbeddableComponentManager componentManager;

    public XWikiTransformer() {
    }

    private ComponentManager getComponentManager() throws Exception {
        if (componentManager == null) {
            componentManager = new EmbeddableComponentManager();
            componentManager.initialize(this.getClass().getClassLoader());
        }
        return componentManager;

    }


    public void transform(String syntax, Reader reader, Writer writer)
            throws RenderingException {
        try {

            if (syntax == null) {
                syntax = Syntax.XWIKI_2_1.toIdString(); // default to xwiki syntax
            }

            Parser parser = getComponentManager().getInstance(Parser.class, syntax);
            XDOM xdom = parser.parse(reader);

            // Execute the Macro Transformation to execute Macros.
            Transformation transformation = getComponentManager().getInstance(Transformation.class, "macro");
            TransformationContext txContext = new TransformationContext(xdom, parser.getSyntax());
            transformation.transform(xdom, txContext);

            // Convert input in XWiki Syntax 2.1 into XHTML. The result is stored in the printer.
            WikiPrinter printer = new DefaultWikiPrinter();
            BlockRenderer renderer = getComponentManager().getInstance(BlockRenderer.class, Syntax.XHTML_1_0.toIdString());
            renderer.render(xdom, printer);

            // Write
            writer.write(printer.toString());
        } catch (Exception e) {
            throw new RenderingException(e);
        }
    }

    public void transform(String syntax, URL url, Writer writer)
            throws RenderingException {
        Reader reader = null;
        try {
            InputStream in = url.openStream();
            reader = new BufferedReader(new InputStreamReader(in));
            transform(syntax, reader, writer);
        } catch (Exception e) {
            throw new RenderingException(e);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception e) {}
            }
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void execute(Environment env, Map params, TemplateModel[] loopVars,
            TemplateDirectiveBody body) throws TemplateException, IOException {

        String syntax = null;
        SimpleScalar scalar = (SimpleScalar)params.get("syntax");
        if (scalar != null) {
            syntax = scalar.getAsString();
        }

        scalar = (SimpleScalar) params.get("src");
        String src = null;
        if (scalar != null) {
            src = scalar.getAsString();
        }

        ComplexPropertyTemplate complex = (ComplexPropertyTemplate) params.get("property");
        Property property = null;
        if (complex != null) {
            property = (Property)complex.getAdaptedObject(null);
        }

        FreemarkerEngine engine = (FreemarkerEngine)env.getCustomAttribute(FreemarkerEngine.RENDERING_ENGINE_KEY);
        if (engine == null) {
            throw new TemplateModelException("Not in a nuxeo rendering context");
        }

        try {
            if (property != null) {
                //TODO XXX implement property support (with caching)
                throw new UnsupportedOperationException("Not Yet Implemented");
//                URL url = PropertyURL.getURL(ctxModel.getDocument(), property.getPath());
//                tr.transform(url, env.getOut(), ctxModel.getContext());
            } else if (src == null) {
                if (body == null) {
                    throw new TemplateModelException(
                            "Transform directive must have either a content either a valid 'src' attribute");
                }
                // render body to get back the result
                StringWriter writer = new StringWriter();
                body.render(writer);
                String content = writer.getBuffer().toString();
                transform(syntax, new StringReader(content), env.getOut());
            } else {
                if (src.contains(":/")) {
                    URL url = engine.getResourceLocator().getResourceURL(src);
                    if (url != null) {
                        transform(syntax, url, env.getOut());
                    } else {
                        throw new IllegalArgumentException("Cannot resolve the src attribute: "+src);
                    }
                } else {
                    File file = engine.getResourceLocator().getResourceFile(src);
                    if (file != null) {
                        transform(syntax, file.toURI().toURL(), env.getOut());
                    } else {
                        throw new IllegalArgumentException("Cannot resolve the src attribute: "+src);
                    }
                }
            }
        } catch (RenderingException e) {
            throw new TemplateException("Running wiki transformer failed", e, env);
        }
    }

}
