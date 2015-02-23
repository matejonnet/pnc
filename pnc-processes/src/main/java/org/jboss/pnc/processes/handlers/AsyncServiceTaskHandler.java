package org.jboss.pnc.processes.handlers;

import org.jboss.logging.Logger;
import org.jbpm.bpmn2.handler.ServiceTaskHandler;
import org.jbpm.bpmn2.handler.WorkItemHandlerRuntimeException;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-10.
 */
public class AsyncServiceTaskHandler extends ServiceTaskHandler {

    Logger logger = Logger.getLogger(AsyncServiceTaskHandler.class);

    @Override
    public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
        String service = (String) workItem.getParameter("Interface");
        String operation = (String) workItem.getParameter("Operation");
        String parameterType = (String) workItem.getParameter("ParameterType");
        Object parameter = workItem.getParameter("Parameter");
        try {
            Class<?> c = Class.forName(service);
            Constructor<?> constructor = c.getConstructor(ServiceTaskCompleteHandler.class);
            Object instance = constructor.newInstance(new ServiceTaskCompleteHandler(manager, workItem));
            Class<?>[] classes = null;
            Object[] params = null;
            if (parameterType != null) {
                classes = new Class<?>[] {
                        Class.forName(parameterType)

                };
                params = new Object[] {
                        parameter
                };
            }
            Method method = c.getMethod(operation, classes);
            method.invoke(instance, params);
        } catch (ClassNotFoundException cnfe) {
            handleException(cnfe, service, operation, parameterType, parameter);
        } catch (InstantiationException ie) {
            handleException(ie, service, operation, parameterType, parameter);
        } catch (IllegalAccessException iae) {
            handleException(iae, service, operation, parameterType, parameter);
        } catch (NoSuchMethodException nsme) {
            handleException(nsme, service, operation, parameterType, parameter);
        } catch (InvocationTargetException ite) {
            handleException(ite, service, operation, parameterType, parameter);
        } catch( Throwable cause ) {
            handleException(cause, service, operation, parameterType, parameter);
        }
    }

    private void handleException(Throwable cause, String service, String operation, String paramType, Object param) {
        logger.debugf("Handling exception {} inside service {} and operation {} with param type {} and value {}",
                cause.getMessage(), service, operation, paramType, param);
        WorkItemHandlerRuntimeException wihRe;
        if( cause instanceof InvocationTargetException ) {
            Throwable realCause = cause.getCause();
            wihRe = new WorkItemHandlerRuntimeException(realCause);
            wihRe.setStackTrace(realCause.getStackTrace());
        } else {
            wihRe = new WorkItemHandlerRuntimeException(cause);
            wihRe.setStackTrace(cause.getStackTrace());
        }
        wihRe.setInformation("Interface", service);
        wihRe.setInformation("Operation", operation);
        wihRe.setInformation("ParameterType", paramType);
        wihRe.setInformation("Parameter", param);
        wihRe.setInformation(WorkItemHandlerRuntimeException.WORKITEMHANDLERTYPE, this.getClass().getSimpleName());
        throw wihRe;

    }
}
