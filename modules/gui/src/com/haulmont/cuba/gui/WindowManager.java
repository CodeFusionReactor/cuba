/*
 * Copyright (c) 2008 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.

 * Author: Dmitry Abramov
 * Created: 26.01.2009 11:14:00
 * $Id$
 */
package com.haulmont.cuba.gui;

import com.haulmont.bali.util.ReflectionHelper;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.core.sys.AppContext;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.config.WindowInfo;
import com.haulmont.cuba.gui.data.*;
import com.haulmont.cuba.gui.data.impl.DatasourceImplementation;
import com.haulmont.cuba.gui.data.impl.DsContextImplementation;
import com.haulmont.cuba.gui.data.impl.GenericDataService;
import com.haulmont.cuba.gui.settings.SettingsImpl;
import com.haulmont.cuba.gui.xml.XmlInheritanceProcessor;
import com.haulmont.cuba.gui.xml.data.DsContextLoader;
import com.haulmont.cuba.gui.xml.layout.ComponentLoader;
import com.haulmont.cuba.gui.xml.layout.LayoutLoader;
import com.haulmont.cuba.gui.xml.layout.LayoutLoaderConfig;
import com.haulmont.cuba.gui.xml.layout.loaders.ComponentLoaderContext;
import com.haulmont.cuba.security.entity.PermissionType;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.dom4j.Document;
import org.dom4j.Element;

import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * GenericUI class intended for creating and opening application screens.
 */
public abstract class WindowManager implements Serializable {

    /**
     * How to open a screen: {@link #NEW_TAB}, {@link #THIS_TAB}, {@link #DIALOG}
     */
    public enum OpenType {
        /**
         * In new tab for TABBED mode, replace current screen for SINGLE mode
         */
        NEW_TAB,
        /**
         * On top of the current conversation stack
         */
        THIS_TAB,
        /**
         * In modal dialog
         */
        DIALOG
    }

    public interface WindowCloseListener extends Serializable {
        void onWindowClose(Window window, boolean anyOpenWindowExist);
    }

    private transient DataService defaultDataService;

    protected Scripting scripting = AppBeans.get(Scripting.NAME);

    protected Resources resources = AppBeans.get(Resources.NAME);

    private DialogParams dialogParams;

    protected List<WindowCloseListener> listeners = new ArrayList<WindowCloseListener>();

    protected WindowManager() {
        dialogParams = createDialogParams();
    }

    public synchronized DataService getDefaultDataService() {
        if (defaultDataService == null) {
            defaultDataService = createDefaultDataService();
        }
        return defaultDataService;
    }

    protected DataService createDefaultDataService() {
        return new GenericDataService();
    }

    public abstract Collection<Window> getOpenWindows();

    protected Integer getHash(WindowInfo windowInfo, Map<String, Object> params) {
        return windowInfo.hashCode() + params.hashCode();
    }

    protected Window createWindow(WindowInfo windowInfo, Map<String, Object> params, LayoutLoaderConfig layoutConfig) {
        checkPermission(windowInfo);

        String templatePath = windowInfo.getTemplate();

        InputStream stream = resources.getResourceAsStream(templatePath);
        if (stream == null) {
            stream = getClass().getResourceAsStream(templatePath);
            if (stream == null) {
                throw new RuntimeException("Bad template path: " + templatePath);
            }
        }

        Document document = null;
        try {
            document = LayoutLoader.parseDescriptor(stream, params);
        } finally {
            IOUtils.closeQuietly(stream);
        }
        XmlInheritanceProcessor processor = new XmlInheritanceProcessor(document, params);
        Element element = processor.getResultRoot();

        MetadataHelper.deployViews(element);

        final DsContext dsContext = loadDsContext(element);
        final ComponentLoaderContext componentLoaderContext = new ComponentLoaderContext(dsContext, params);

        final Window window = loadLayout(windowInfo.getTemplate(), element, componentLoaderContext, layoutConfig);

        window.setId(windowInfo.getId());

        initDatasources(window, dsContext, params);

        FrameContext frameContext = new FrameContext(window, params);
        window.setContext(frameContext);
        dsContext.setWindowContext(frameContext);

        final Window windowWrapper = wrapByCustomClass(window, element, params);
        componentLoaderContext.setFrame(windowWrapper);
        componentLoaderContext.executePostInitTasks();

        if (ConfigProvider.getConfig(GlobalConfig.class).getTestMode()) {
            initDebugIds(window);
        }

        // apply ui permissions
        PermissionsApplyHelper.applyUiPermissions(window);

        return windowWrapper;
    }

    protected void initDebugIds(final Window window) {
        ComponentsHelper.walkComponents(window, new ComponentVisitor() {
            public void visit(Component component, String name) {
                component.setDebugId(window.getId() + "." + name);
            }
        });
    }

    private void checkPermission(WindowInfo windowInfo) {
        boolean permitted = UserSessionProvider.getUserSession().isScreenPermitted(
                AppConfig.getClientType(),
                windowInfo.getId()
        );
        if (!permitted)
            throw new AccessDeniedException(PermissionType.SCREEN, windowInfo.getId());
    }

    protected void initDatasources(final Window window, DsContext dsContext, Map<String, Object> params) {
        window.setDsContext(dsContext);

        for (Datasource ds : dsContext.getAll()) {
            if (Datasource.State.NOT_INITIALIZED.equals(ds.getState()) && ds instanceof DatasourceImplementation) {
                ((DatasourceImplementation) ds).initialized();
            }
        }
    }

    protected Window loadLayout(String descriptorPath, Element rootElement, ComponentLoader.Context context, LayoutLoaderConfig layoutConfig) {
        final LayoutLoader layoutLoader = new LayoutLoader(context, AppConfig.getFactory(), layoutConfig);
        layoutLoader.setLocale(getLocale());
        if (!StringUtils.isEmpty(descriptorPath)) {
            String path = descriptorPath.replaceAll("/", ".");
            int start = path.startsWith(".") ? 1 : 0;
            path = path.substring(start, path.lastIndexOf("."));

            layoutLoader.setMessagesPack(path);
        }

        final Window window = (Window) layoutLoader.loadComponent(rootElement, null);
        return window;
    }

    protected DsContext loadDsContext(Element element) {
        DataService dataService;

        String dataserviceClass = element.attributeValue("dataservice");
        if (StringUtils.isEmpty(dataserviceClass)) {
            final Element dataserviceElement = element.element("dataservice");
            if (dataserviceElement != null) {
                dataserviceClass = dataserviceElement.getText();
                if (StringUtils.isEmpty(dataserviceClass)) {
                    throw new IllegalStateException("Can't find dataservice class name");
                }
                dataService = createDataservice(dataserviceClass, dataserviceElement);
            } else {
                dataService = getDefaultDataService();
            }
        } else {
            dataService = createDataservice(dataserviceClass, null);
        }

        final DsContextLoader dsContextLoader = new DsContextLoader(dataService);
        final DsContext dsContext = dsContextLoader.loadDatasources(element.element("dsContext"), null);

        return dsContext;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    protected DataService createDataservice(String dataserviceClass, Element element) {
        DataService dataService;

        final Class<Object> aClass = ReflectionHelper.getClass(dataserviceClass);
        try {
            dataService = (DataService) aClass.newInstance();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        return dataService;
    }

    protected Window createWindow(WindowInfo windowInfo, Map params) {
        final Window window;
        try {
            window = (Window) windowInfo.getScreenClass().newInstance();
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        window.setId(windowInfo.getId());
        try {
            ReflectionHelper.invokeMethod(window, "init", params);
        } catch (NoSuchMethodException e) {
            // Do nothing
        }

        // apply ui permissions
        PermissionsApplyHelper.applyUiPermissions(window);

        return window;
    }

    protected Window createWindowByScreenClass(WindowInfo windowInfo, Map<String, Object> params) {
        Class screenClass = windowInfo.getScreenClass();

        Class[] paramTypes = ReflectionHelper.getParamTypes(params);
        Constructor constructor = null;
        try {
            constructor = screenClass.getConstructor(paramTypes);
        } catch (NoSuchMethodException e) {
            //
        }

        Object obj;
        try {
            if (constructor == null) {
                obj = screenClass.newInstance();
            } else {
                obj = constructor.newInstance(params);
            }
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        if (obj instanceof Callable) {
            try {
                Window window = ((Callable<Window>) obj).call();
                return window;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else if (obj instanceof Runnable) {
            ((Runnable) obj).run();
            return null;
        } else
            throw new IllegalStateException("Screen class must be an instance of Callable<Window> or Runnable");
    }

    public boolean windowExist(WindowInfo windowInfo, Map<String,Object> params){
        return (getWindow(getHash(windowInfo, params)) != null);
    }

    public <T extends Window> T openWindow(WindowInfo windowInfo, WindowManager.OpenType openType, Map<String, Object> params) {
        checkCanOpenWindow(windowInfo, openType, params);
        Integer hashCode = getHash(windowInfo, params);
        params = createParametersMap(windowInfo, params);
        String template = windowInfo.getTemplate();

        Window window;

        if (template != null) {
            //noinspection unchecked
            window = createWindow(windowInfo, params, LayoutLoaderConfig.getWindowLoaders());
            window.setId(windowInfo.getId());
            String caption = loadCaption(window, params);
            String description = loadDescription(window, params);
            putToWindowMap(window, hashCode);
            showWindow(window, caption, description, openType, windowInfo.getMultipleOpen());
            return (T) window;
        } else {
            Class screenClass = windowInfo.getScreenClass();
            if (screenClass != null) {
                //noinspection unchecked
                window = createWindowByScreenClass(windowInfo, params);
                putToWindowMap(window, hashCode);
                return (T) window;
            } else
                return null;
        }
    }

    protected abstract void putToWindowMap(Window window, Integer hashCode);

    protected abstract Window getWindow(Integer hashCode);

    protected abstract void checkCanOpenWindow(WindowInfo windowInfo, OpenType openType, Map<String, Object> params);

    protected String loadCaption(Window window, Map<String, Object> params) {
        String caption = window.getCaption();
        if (!StringUtils.isEmpty(caption)) {
            caption = TemplateHelper.processTemplate(caption, params);
        } else {
            caption = WindowParams.CAPTION.getString(params);
            if (StringUtils.isEmpty(caption)) {
                String msgPack = window.getMessagesPack();
                if (msgPack != null) {
                    caption = MessageProvider.getMessage(msgPack, "caption");
                    if (!"caption".equals(caption)) {
                        caption = TemplateHelper.processTemplate(caption, params);
                    }
                }
            } else {
                caption = TemplateHelper.processTemplate(caption, params);
            }
        }
        window.setCaption(caption);

        return caption;
    }

    protected String loadDescription(Window window, Map<String, Object> params) {
        String description = window.getDescription();
        if (!StringUtils.isEmpty(description)) {
            return TemplateHelper.processTemplate(description, params);
        } else {
            description = WindowParams.DESCRIPTION.getString(params);
            if (StringUtils.isEmpty(description)) {
                description = null;
            } else {
                description = TemplateHelper.processTemplate(description, params);
            }
        }
        window.setDescription(description);

        return description;
    }

    public <T extends Window> T openEditor(WindowInfo windowInfo, Entity item, OpenType openType,
                                           Datasource parentDs) {
        //noinspection unchecked
        return (T) openEditor(windowInfo, item, openType, Collections.<String, Object>emptyMap(), parentDs);
    }

    public <T extends Window> T openEditor(WindowInfo windowInfo, Entity item, OpenType openType) {
        //noinspection unchecked
        return (T) openEditor(windowInfo, item, openType, Collections.<String, Object>emptyMap());
    }

    public <T extends Window> T openEditor(WindowInfo windowInfo, Entity item, OpenType openType, Map<String, Object> params) {
        //noinspection unchecked
        return (T) openEditor(windowInfo, item, openType, params, null);
    }

    public <T extends Window> T openEditor(WindowInfo windowInfo, Entity item,
                                           OpenType openType, Map<String, Object> params,
                                           Datasource parentDs)
    {
        checkCanOpenWindow(windowInfo, openType, params);

        Integer hashCode = getHash(windowInfo, params);
        params = createParametersMap(windowInfo, params);
        String template = windowInfo.getTemplate();
        Window window = getWindow(hashCode);
        if (window != null) {
            String caption = loadCaption(window, params);
            String description = loadDescription(window, params);

            showWindow(window, caption, description, openType, false);
            return (T) window;
        }

        params = createParametersMap(windowInfo, params);
        WindowParams.ITEM.set(params, item instanceof Datasource ? ((Datasource) item).getItem() : item);

        if (template != null) {
            window = createWindow(windowInfo, params, LayoutLoaderConfig.getEditorLoaders());
        } else {
            Class windowClass = windowInfo.getScreenClass();
            if (windowClass != null) {
                window = createWindow(windowInfo, params);
                if (!(window instanceof Window.Editor)) {
                    throw new IllegalStateException(
                            String.format("Class %s does't implement Window.Editor interface", windowClass));
                }
            } else {
                throw new IllegalStateException("Invalid WindowInfo: " + windowInfo);
            }
        }
        ((Window.Editor) window).setParentDs(parentDs);
        ((Window.Editor) window).setItem(item);

        final String caption = loadCaption(window, params);
        final String description = loadDescription(window, params);
        showWindow(window, caption, description, openType, false);

        //noinspection unchecked
        return (T) window;
    }

    public <T extends Window> T openLookup(
            WindowInfo windowInfo, Window.Lookup.Handler handler,
            OpenType openType, Map<String, Object> params) {
        checkCanOpenWindow(windowInfo, openType, params);

        params = createParametersMap(windowInfo, params);

        String template = windowInfo.getTemplate();
        Window window;

        if (template != null) {
            window = createWindow(windowInfo, params, LayoutLoaderConfig.getLookupLoaders());

            final Element element = ((Component.HasXmlDescriptor) window).getXmlDescriptor();
            final String lookupComponent = element.attributeValue("lookupComponent");
            if (!StringUtils.isEmpty(lookupComponent)) {
                final Component component = window.getComponent(lookupComponent);
                ((Window.Lookup) window).setLookupComponent(component);
            }
        } else {
            Class windowClass = windowInfo.getScreenClass();
            if (windowClass != null) {
                window = createWindow(windowInfo, params);
                if (!(window instanceof Window.Lookup)) {
                    throw new IllegalStateException(
                            String.format("Class %s does't implement Window.Lookup interface", windowClass));
                }
            } else {
                throw new IllegalStateException("Invalid WindowInfo: " + windowInfo);
            }
        }
        window.setId(windowInfo.getId());
        ((Window.Lookup) window).setLookupHandler(handler);

        final String caption = loadCaption(window, params);
        final String description = loadDescription(window, params);

        showWindow(window, caption, description, openType, false);

        //noinspection unchecked
        return (T) window;
    }

    public <T extends IFrame> T openFrame(
            Window window,
            Component parent,
            WindowInfo windowInfo
    ) {
        return (T) openFrame(window, parent, windowInfo, Collections.<String, Object>emptyMap());
    }

    public <T extends IFrame> T openFrame(
            Window window,
            Component parent,
            WindowInfo windowInfo,
            Map<String, Object> params
    ) {
        //Parameters can be useful later
        params = createParametersMap(windowInfo, params);

        String src = windowInfo.getTemplate();

        ComponentLoaderContext context = new ComponentLoaderContext(window.getDsContext(), params);

        final LayoutLoader loader =
                new LayoutLoader(context, AppConfig.getFactory(), LayoutLoaderConfig.getFrameLoaders());
        loader.setLocale(getLocale());
        loader.setMessagesPack(window.getMessagesPack());

        InputStream stream = resources.getResourceAsStream(src);
        if (stream == null) {
            stream = getClass().getResourceAsStream(src);
            if (stream == null) {
                throw new RuntimeException("Bad template path: " + src);
            }
        }

        final IFrame component;
        try {
            component = (IFrame) loader.loadComponent(stream, parent, context.getParams());
        } finally {
            IOUtils.closeQuietly(stream);
        }
        if (component.getMessagesPack() == null) {
            component.setMessagesPack(window.getMessagesPack());
        }

        component.setFrame(window);
        context.setFrame(component);
        context.executePostInitTasks();

        if (parent != null)
            showFrame(parent, component);

        return (T) component;
    }

    protected Map<String, Object> createParametersMap(WindowInfo windowInfo, Map<String, Object> params) {
        final Map<String, Object> map = new HashMap<String, Object>(params.size());

        final Element element = windowInfo.getDescriptor();
        if (element != null) {
            final Element paramsElement = element.element("params");
            if (paramsElement != null) {
                @SuppressWarnings({"unchecked"})
                final List<Element> paramElements = paramsElement.elements("param");
                for (Element paramElement : paramElements) {
                    final String name = paramElement.attributeValue("name");
                    final String value = paramElement.attributeValue("value");
                    if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                        Boolean booleanValue = Boolean.valueOf(value);
                        map.put(name, booleanValue);
                    } else {
                        map.put(name, value);
                    }
                }
            }
        }

        map.putAll(params);

        return map;
    }

    protected DialogParams createDialogParams() {
        return new DialogParams();
    }

    public DialogParams getDialogParams() {
        return dialogParams;
    }

    protected void fireListeners(Window window, boolean anyOpenWindowExist) {
        for (WindowCloseListener wcl : listeners) {
            wcl.onWindowClose(window, anyOpenWindowExist);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public <T extends Window> T openWindow(WindowInfo windowInfo, OpenType openType) {
        //noinspection unchecked
        return (T) openWindow(windowInfo, openType, Collections.<String, Object>emptyMap());
    }

    public <T extends Window> T openLookup(WindowInfo windowInfo, Window.Lookup.Handler handler, OpenType openType) {
        //noinspection unchecked
        return (T) openLookup(windowInfo, handler, openType, Collections.<String, Object>emptyMap());
    }

    protected abstract void showWindow(Window window, String caption, OpenType openType, boolean multipleOpen);

    protected abstract void showWindow(Window window, String caption, String description, OpenType openType, boolean multipleOpen);

    protected abstract void showFrame(Component parent, IFrame frame);

    protected void afterShowWindow(Window window) {
        if (!WindowParams.DISABLE_APPLY_SETTINGS.getBool(window.getContext())) {
            window.applySettings(new SettingsImpl(window.getId()));
        }
        if (!WindowParams.DISABLE_RESUME_SUSPENDED.getBool(window.getContext())) {
            ((DsContextImplementation) window.getDsContext()).resumeSuspended();
        }
    }

    public abstract void close(Window window);

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected Locale getLocale() {
        return UserSessionProvider.getUserSession().getLocale();
    }

    protected Window wrapByCustomClass(Window window, Element element, Map<String, Object> params) {
        final String screenClass = element.attributeValue("class");
        if (!StringUtils.isBlank(screenClass)) {
            Class<Window> aClass = scripting.loadClass(screenClass);
            if (aClass == null)
                aClass = ReflectionHelper.getClass(screenClass);

            Window wrappingWindow = ((WrappedWindow) window).wrapBy(aClass);

            if (wrappingWindow instanceof AbstractWindow) {
                Element companionsElem = element.element("companions");
                if (companionsElem != null) {
                    initCompanion(companionsElem, (AbstractWindow) wrappingWindow);
                }
            }

            ControllerDependencyInjector dependencyInjector = new ControllerDependencyInjector(wrappingWindow);
            dependencyInjector.inject();

            try {
                ReflectionHelper.invokeMethod(wrappingWindow, "init", params);
            } catch (NoSuchMethodException e) {
                // do nothing
            }

            // apply ui permissions
            PermissionsApplyHelper.applyUiPermissions(wrappingWindow);

            return wrappingWindow;
        } else {
            return window;
        }
    }

    protected void initCompanion(Element companionsElem, AbstractWindow window) {
        Element element = companionsElem.element(AppConfig.getClientType().toString().toLowerCase());
        if (element != null) {
            String className = element.attributeValue("class");
            if (!StringUtils.isBlank(className)) {
                Class aClass = scripting.loadClass(className);
                Object companion;
                try {
                    if (AbstractCompanion.class.isAssignableFrom(aClass)) {
                        Constructor constructor = aClass.getConstructor(new Class[]{AbstractFrame.class});
                        companion = constructor.newInstance(window);
                    } else {
                        companion = aClass.newInstance();
                        window.setCompanion(companion);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public abstract void showNotification(String caption, IFrame.NotificationType type);

    public abstract void showNotification(String caption, String description, IFrame.NotificationType type);

    public abstract void showMessageDialog(String title, String message, IFrame.MessageType messageType);

    public abstract void showOptionDialog(String title, String message, IFrame.MessageType messageType, Action[] actions);
}
