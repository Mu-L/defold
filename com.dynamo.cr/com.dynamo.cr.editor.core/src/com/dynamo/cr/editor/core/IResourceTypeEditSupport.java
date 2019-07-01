package com.dynamo.cr.editor.core;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Message;

/**
 * Edit support class for resource types
 * @author chmu
 *
 */
public interface IResourceTypeEditSupport {

    /**
     * Create a template message for a message type.
     * @param descriptor descriptor to create message for
     * @return message instance. null if not supported
     */
    Message getTemplateMessageFor(Descriptor descriptor);

    /**
     * Get label abbreviation text for a message
     * @param message message to get abbreviation for
     * @return message abbreviation text
     */
    String getLabelText(Message message);
}