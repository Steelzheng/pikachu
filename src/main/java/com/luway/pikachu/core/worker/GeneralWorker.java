package com.luway.pikachu.core.worker;

import com.luway.pikachu.core.annotations.CssPath;
import com.luway.pikachu.core.annotations.MatchUrl;
import com.luway.pikachu.core.annotations.WorkerType;
import com.luway.pikachu.core.annotations.Xpath;
import com.luway.pikachu.core.exception.SimpleException;
import com.luway.pikachu.core.pipeline.BasePipeline;
import com.luway.pikachu.core.worker.bean.BaseWorker;
import com.luway.pikachu.core.worker.bean.Target;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * @author zhenggm
 * @create 2018-03-01 上午 11:18
 **/


public class GeneralWorker extends BaseWorker implements Worker {
    private final static Logger log = LoggerFactory.getLogger(GeneralWorker.class);

    public GeneralWorker(String id, Class<?> bean) {
        this.id = id;
        super.setType(WorkerType.GENERAL);
        if (bean == null) {
            throw new RuntimeException("[error] class is null");
        }
        load(bean);
    }

    @Override
    public GeneralWorker cookies(Map<String, String> cookies) {
        super.setCookies(cookies);
        return this;
    }

    private GeneralWorker load(Class<?> bean) {
        MatchUrl u = bean.getAnnotation(MatchUrl.class);
        log.debug(bean.getName() + "is load");
        this.url = u.url();
        if (StringUtils.isEmpty(this.url)) {
            throw new RuntimeException("[error] url can not be null");
        }
        super.setMethod(u.method());
        this.loadJs = u.loadJs();
        return attr(bean);
    }

    public GeneralWorker attr(Class<?> bean) {
        attr = new HashMap<String, Target>(16);
        try {
            Field[] fields = bean.getDeclaredFields();
            for (Field field : fields) {
                boolean fieldHasAnno = field.isAnnotationPresent(CssPath.class);
                if (fieldHasAnno) {
                    CssPath cssPath = field.getAnnotation(CssPath.class);
                    //输出注解属性
                    Target t = new Target(field.getName().toString(), cssPath.selector(), null);
                    attr.put(field.getName(), t);
                    log.debug(field.getName() + ":" + field.getType().toString() + ":" + cssPath.selector());
                }
                boolean fieldHasXpath = field.isAnnotationPresent(Xpath.class);
                if (fieldHasXpath) {
                    Xpath xpath = field.getAnnotation(Xpath.class);
                    //输出注解属性
                    Target t = new Target(field.getName(), null, xpath.xpath());
                    attr.put(field.getName(), t);
                    log.debug(field.getName() + ":" + field.getType().toString() + ":" + xpath.xpath());
                }
            }
        } catch (Exception e) {
            log.error("attr error", e);
            throw new SimpleException(e);
        }
        return this;
    }

    @Override
    public GeneralWorker addPipeline(BasePipeline pipeline) {
        this.pipeline = pipeline;
        return this;
    }

    @Override
    public Boolean validate() {
        if (pipeline == null) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public String toString() {
        return "GeneralWorker{" +
                "method=" + super.getMethod() +
                ", id='" + id + '\'' +
                ", url='" + url + '\'' +
                ", attr=" + attr +
                ", cookies=" + super.getCookies() +
                ", pipeline=" + pipeline +
                ", loadJs=" + loadJs +
                '}';
    }

}
