package org.mule.transport.cifs;

import org.junit.Test;
import org.mule.api.MuleException;
import org.mule.client.DefaultLocalMuleClient;
import org.mule.tck.junit4.FunctionalTestCase;

public class ManualTesting extends FunctionalTestCase {
    @Override
    protected String getConfigResources() {
        return "smb-inbound-file-config.xml";
    }
    
    @Test
    public void test() throws MuleException {
        new DefaultLocalMuleClient(muleContext).request("vm://tmp", 600000);
    }
}
