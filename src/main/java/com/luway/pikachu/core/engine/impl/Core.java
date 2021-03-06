package com.luway.pikachu.core.engine.impl;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.luway.pikachu.core.annotations.MatchUrl;
import com.luway.pikachu.core.engine.AbstractTempMethod;
import com.luway.pikachu.core.exception.SimpleException;
import com.luway.pikachu.core.worker.BathWorker;
import com.luway.pikachu.core.worker.CustomWorker;
import com.luway.pikachu.core.worker.GeneralWorker;
import com.luway.pikachu.core.worker.Worker;
import com.luway.pikachu.core.worker.bean.BaseWorker;
import com.luway.pikachu.core.worker.bean.Target;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.DomSerializer;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

import static com.luway.pikachu.common.DynamicIpUtil.changeMyIp;
import static com.luway.pikachu.common.TimeUtil.sleep;

/**
 * @author : iron
 * @version : 1.0.0
 * @date : 下午3:31 2018/8/1
 */

public class Core extends AbstractTempMethod {
    private final static Logger log = LoggerFactory.getLogger(Core.class);
    private Document doc;
    private volatile Boolean flag = true;

    // 开启代理开关
    private volatile Boolean openIpProxy = false;
    // 随机暂停开关
    private volatile Boolean sleepFlag = false;

    private BlockingQueue<Worker> workerQueue;
    private ExecutorService pikachuPool;

    public Core(ExecutorService pikachuPool, Boolean openIpProxy, Boolean sleepFlag) {
        this.workerQueue = new ArrayBlockingQueue<>(1024);
        this.pikachuPool = pikachuPool;
        this.openIpProxy = openIpProxy;
        this.sleepFlag = sleepFlag;
    }

    protected boolean putWorker(Worker worker) {
        return workerQueue.offer(worker);
    }

    public void start() {
        pikachuPool.execute(new Runnable() {
            @Override
            public void run() {
                while (flag) {
                    // 默认不开启代理IP地址池
                    if (openIpProxy) {
                        changeMyIp();
                    }
                    try {
                        Worker worker = workerQueue.take();
                        if (worker.validate()) {

                            if (worker instanceof GeneralWorker) {
                                GeneralWorker generalWorker = (GeneralWorker) worker;
                                pikachuPool.execute(() -> {
                                    try {
                                        load(generalWorker);
                                    } catch (Exception e) {
                                        log.error("core error", e);
                                    }
                                });
                            }
                            if (worker instanceof BathWorker) {
                                BathWorker bathWorker = (BathWorker) worker;
                                pikachuPool.execute(() -> {
                                    try {
                                        load(bathWorker);
                                    } catch (Exception e) {
                                        log.error("core error", e);
                                    }
                                });
                            }

                            if (worker instanceof CustomWorker) {
                                CustomWorker customWorker = (CustomWorker) worker;
                                pikachuPool.execute(() -> {
                                    try {
                                        load(customWorker);
                                    } catch (Exception e) {
                                        log.error("core error", e);
                                    }
                                });
                            }
                        } else {
                            log.error("this worker's pip is null.[WORKER ID: " + worker.getId() + "]");
                            throw new Exception("this worker's pip is null.[WORKER ID: " + worker.getId() + "]");
                        }
                    } catch (Exception e) {
                        log.error("core error", e);
                        stop();
                    }
                }
            }
        });
    }

    /**
     * 加载批量url方法
     *
     * @param worker
     * @throws Exception
     */
    private void load(BathWorker worker) throws Exception {
        for (String url : worker.getUrlList()) {
            pikachuPool.execute(() -> {
                try {
                    exector(url, worker);
                } catch (Exception e) {
                    log.error("exception", e);
                    throw new SimpleException(e);
                }
            });
        }
    }

    private void exector(String url, BathWorker worker) throws Exception {
        sleep();
        Document doc = null;
        if (MatchUrl.Method.GET.equals(worker.getMethod())) {
            if (worker.getCookies() != null) {
                doc = getConnection(url)
                        .cookies(worker.getCookies())
                        .get();
            } else {
                doc = getConnection(url).get();
            }
        } else if (MatchUrl.Method.POST.equals(worker.getMethod())) {
            if (worker.getCookies() != null) {
                doc = getConnection(url)
                        .cookies(worker.getCookies())
                        .post();
            } else {
                doc = getConnection(url).post();
            }
        }
        if (doc == null) {
            worker.getPipeline().output(null, url);
        }
        Map<String, Elements> target = select(doc, worker.getAttr());
        out(target, url, worker);
    }


    /**
     * 加载通用方法
     *
     * @param worker
     * @throws Exception
     */
    public synchronized void load(BaseWorker worker) throws Exception {
        if (worker.isLoadJs()) {
            loadJs(worker);
        } else {
            loadHtml(worker);
        }
    }

    /**
     * 加载需要js的页面
     *
     * @param worker
     * @throws Exception
     */
    private void loadJs(BaseWorker worker) throws Exception {
        // HtmlUnit 模拟浏览器
        WebClient wc = new WebClient(BrowserVersion.FIREFOX_52);
        wc.setJavaScriptTimeout(100000);
        //接受任何主机连接 无论是否有有效证书
        wc.getOptions().setUseInsecureSSL(true);
        //设置支持javascript脚本
        wc.getOptions().setJavaScriptEnabled(true);
        //禁用css支持
        wc.getOptions().setCssEnabled(false);
        //js运行错误时不抛出异常
        wc.getOptions().setThrowExceptionOnScriptError(false);
        //设置连接超时时间
        wc.getOptions().setTimeout(100000);
        wc.getOptions().setDoNotTrackEnabled(false);
        HtmlPage page = wc.getPage(worker.getUrl());

        String pageAsXml = page.asXml();
        // Jsoup解析处理
        Document doc = Jsoup.parse(pageAsXml, worker.getUrl());
        Map<String, Elements> target = select(doc, worker.getAttr());
        out(target, worker.getUrl(), worker);
    }

    /**
     * 获取html
     *
     * @param worker
     * @throws Exception
     */
    private void loadHtml(BaseWorker worker) throws Exception {
        if (worker.getCookies() == null) {
            if (MatchUrl.Method.GET.equals(worker.getMethod())) {
                doc = getConnection(worker.getUrl())
                        .get();
            } else if (MatchUrl.Method.POST.equals(worker.getMethod())) {
                doc = getConnection(worker.getUrl())
                        .post();
            }
        } else {
            if (MatchUrl.Method.GET.equals(worker.getMethod())) {
                doc = getConnection(worker.getUrl())
                        .cookies(worker.getCookies())
                        .get();
            } else if (MatchUrl.Method.POST.equals(worker.getMethod())) {
                doc = getConnection(worker.getUrl())
                        .cookies(worker.getCookies())
                        .post();
            }
        }
        if (doc == null) {
            worker.getPipeline().output(null, worker.getUrl());
        }
        Map<String, Elements> target = select(doc, worker.getAttr());
        out(target, worker.getUrl(), worker);
    }

    /**
     * 输出到管道中
     *
     * @param target
     * @param worker
     */
    private synchronized void out(Map<String, Elements> target, String url, BaseWorker worker) {
        worker.getPipeline().output(target, url);
        // 将新任务调度到队尾
        if (worker.getPipeline().checkWorker().size() > 0) {
            List<GeneralWorker> workerList = worker.getPipeline().checkWorker();
            for (GeneralWorker worker1 : workerList) {
                workerQueue.offer(worker1);
            }
        }
    }

    private Map<String, Elements> select(Document doc, Map<String, Target> params) throws Exception {
        Map<String, Elements> target = new HashMap<>(16);
        HtmlCleaner hc = new HtmlCleaner();
        TagNode tn = hc.clean(doc.body().html());
        org.w3c.dom.Document dom = new DomSerializer(new CleanerProperties()).createDOM(tn);
        XPath xPath = XPathFactory.newInstance().newXPath();
        for (Map.Entry<String, Target> attr : params.entrySet()) {
            if (null != attr.getValue().getSelector()) {
                Elements elements = doc.select(attr.getValue().getSelector());
                target.put(attr.getValue().getName(), elements);
            }

            if (null != attr.getValue().getXpath()) {
                Elements result = (Elements) xPath.evaluate(attr.getValue().getXpath(),
                        dom, XPathConstants.NODESET);

                target.put(attr.getValue().getName(), result);
            }
        }
        return target;
    }

    public void stop() {
        this.flag = false;
        pikachuPool.shutdown();
    }

    @Override
    protected Document getConnect(String url, MatchUrl.Method method) throws IOException {
        if (MatchUrl.Method.GET.equals(method)) {
            doc = getConnection(url).get();
        } else if (MatchUrl.Method.POST.equals(method)) {
            doc = getConnection(url).post();
        }
        return doc;
    }

    @Override
    protected Document getConnect(String url, MatchUrl.Method method, Map<String, String> cookies) throws IOException {
        if (MatchUrl.Method.GET.equals(method)) {
            doc = getConnection(url)
                    .cookies(cookies)
                    .get();
        } else if (MatchUrl.Method.POST.equals(method)) {
            doc = getConnection(url)
                    .cookies(cookies)
                    .post();
        }
        return doc;
    }

    @Override
    protected Document getConnect(String url, MatchUrl.Method method, Map<String, String> cookies, Map<String, String> headers) throws IOException {
        if (MatchUrl.Method.GET.equals(method)) {
            doc = getConnection(url)
                    .cookies(cookies)
                    .headers(headers)
                    .get();
        } else if (MatchUrl.Method.POST.equals(method)) {
            doc = getConnection(url)
                    .cookies(cookies)
                    .headers(headers)
                    .post();
        }
        return doc;
    }


    private Connection getConnection(String url) {
        // 随机暂停，防止抓取过快对站点造成过大压力
        if (sleepFlag) {
            sleep();
        }
        return Jsoup.connect(url).timeout(300000)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Encoding", "gzip, deflate, sdch")
                .header("Accept-Language", "zh-CN,zh;q=0.8")
                .ignoreContentType(true)
                .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36")
                .validateTLSCertificates(false);
    }

    @Override
    public Queue<Worker> getQueue() {
        return workerQueue;
    }
}
