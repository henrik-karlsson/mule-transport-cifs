package org.mule.transport.cifs;

import org.mule.api.MuleContext;
import org.mule.api.MuleEvent;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.construct.AbstractPipeline;
import org.mule.exception.TemplateMessagingExceptionStrategy;
import org.mule.processor.strategy.SynchronousProcessingStrategy;

public class NonConsumingRollbackExceptionStrategy extends TemplateMessagingExceptionStrategy {
    @Override
    protected MuleEvent beforeRouting(Exception exception, MuleEvent event) {
        rollback(exception);
        return event;
    }

    @Override
    protected void logException(Throwable t) {
        // NOP - exception should be logged in strategy
    }

    @Override
    protected void doInitialise(MuleContext muleContext) throws InitialisationException {
        super.doInitialise(muleContext);
        AbstractPipeline pipeline = (AbstractPipeline) flowConstruct;
        if (!(pipeline.getProcessingStrategy() instanceof SynchronousProcessingStrategy)) {
            logger.warn("Using NonConsumingRollbackExceptionStrategy requires a synchronous processing strategy. " +
                    "Processing strategy for " + pipeline.getName() + " re-configured to synchronous.");
            pipeline.setProcessingStrategy(new SynchronousProcessingStrategy());
        }
    }
}
