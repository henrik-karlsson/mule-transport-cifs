package org.mule.transport.cifs;

import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.processor.MessageProcessor;

public class ExceptionThrower implements MessageProcessor {
    @Override
    public MuleEvent process(MuleEvent event) throws MuleException {
        throw new RuntimeException("I failed.");
    }
}
