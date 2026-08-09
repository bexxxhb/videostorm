package de.videostorm.config;

import de.neuland.pug4j.PugConfiguration;
import de.neuland.pug4j.spring.template.SpringTemplateLoader;
import de.neuland.pug4j.spring.view.PugViewResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.ViewResolver;

/**
 * Wires Pug4j into Spring MVC. There is no Boot starter for Pug4j, so the loader, the
 * configuration and the view resolver are declared by hand.
 */
@Configuration
public class PugViewConfiguration {

    private static final String TEMPLATE_PATH = "classpath:/templates/";
    private static final String TEMPLATE_SUFFIX = ".pug";
    private static final String CONTENT_TYPE = "text/html;charset=UTF-8";

    /**
     * On by default: in the container the templates are packaged in the jar and can never
     * change. Set {@code videostorm.web.template-caching=false} when running locally so that
     * template edits take effect without a restart.
     */
    @Value("${videostorm.web.template-caching:true}")
    private boolean templateCaching;

    @Bean
    public SpringTemplateLoader pugTemplateLoader() {
        SpringTemplateLoader loader = new SpringTemplateLoader();
        loader.setTemplateLoaderPath(TEMPLATE_PATH);
        loader.setSuffix(TEMPLATE_SUFFIX);
        loader.setEncoding("UTF-8");
        return loader;
    }

    @Bean
    public PugConfiguration pugConfiguration(SpringTemplateLoader pugTemplateLoader) {
        PugConfiguration configuration = new PugConfiguration();
        configuration.setTemplateLoader(pugTemplateLoader);
        configuration.setCaching(templateCaching);
        return configuration;
    }

    @Bean
    public ViewResolver pugViewResolver(PugConfiguration pugConfiguration) {
        PugViewResolver resolver = new PugViewResolver();
        resolver.setConfiguration(pugConfiguration);
        resolver.setContentType(CONTENT_TYPE);
        return resolver;
    }
}
