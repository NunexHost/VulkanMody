package net.vulkanmod.vulkan.queue;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.Vulkan;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class CommandPool {
    long id;

    private final List<CommandBuffer> commandBuffers = new ObjectArrayList<>();
    private final java.util.Queue<CommandBuffer> availableCmdBuffers = new ArrayDeque<>();

    CommandPool(int queueFamilyIndex) {
        this.createCommandPool(queueFamilyIndex);
    }

    public void createCommandPool(int familyIndex) {

        try(MemoryStack stack = stackPush()) {

            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.callocStack(stack);
            poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
            poolInfo.queueFamilyIndex(familyIndex);
            poolInfo.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);

            LongBuffer pCommandPool = stack.mallocLong(1);

            if (vkCreateCommandPool(Vulkan.getDevice(), poolInfo, null, pCommandPool) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create command pool");
            }

            this.id = pCommandPool.get(0);
        }
    }

    public CommandBuffer beginCommands() {

        try(MemoryStack stack = stackPush()) {
            final int size = 10;

            if(availableCmdBuffers.isEmpty()) {

                VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.callocStack(stack);
                allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
                allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
                allocInfo.commandPool(id);
                allocInfo.commandBufferCount(size);

                PointerBuffer pCommandBuffer = stack.mallocPointer(size);
                vkAllocateCommandBuffers(Vulkan.getDevice(), allocInfo, pCommandBuffer);

                VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.callocStack(stack);
                fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
                fenceInfo.flags(VK_FENCE_CREATE_SIGNALED_BIT);

                for(int i = 0; i < size; ++i) {
                    LongBuffer pFence = stack.mallocLong(size);
                    vkCreateFence(Vulkan.getDevice(), fenceInfo, null, pFence);

                    CommandBuffer commandBuffer = new CommandBuffer(new VkCommandBuffer(pCommandBuffer.get(i), Vulkan.getDevice()), pFence.get(0));
                    commandBuffer.handle = new VkCommandBuffer(pCommandBuffer.get(i), Vulkan.getDevice());
                    commandBuffers.add(commandBuffer);
                    availableCmdBuffers.add(commandBuffer);
                }

            }

            CommandBuffer commandBuffer = availableCmdBuffers.poll();

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.callocStack(stack);
            beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            beginInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

            vkBeginCommandBuffer(commandBuffer.handle, beginInfo);
            commandBuffer.recording=true;
//            current++;

            return commandBuffer;
        }
    }

    public synchronized long submitCommands2(VkQueue queue, ObjectArrayList<CommandBuffer> commandBuffer, long pSignalSemaphore, long pWaitSemaphore) {

        try(MemoryStack stack = stackPush()) {
            PointerBuffer pointerBuffer = stack.mallocPointer(commandBuffer.size());
            for(var handle : commandBuffer){
                vkEndCommandBuffer(handle.getHandle());
                pointerBuffer.put(handle.getHandle());
            }
            pointerBuffer.rewind();


            VkSubmitInfo submitInfo = VkSubmitInfo.callocStack(stack);
            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
            submitInfo.pWaitSemaphores(stack.longs(pWaitSemaphore));
            submitInfo.pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_TRANSFER_BIT));
            submitInfo.pSignalSemaphores(stack.longs(pSignalSemaphore));
            submitInfo.pCommandBuffers(pointerBuffer);

            vkQueueSubmit(queue, submitInfo, VK_NULL_HANDLE);
            //vkQueueWaitIdle(graphicsQueue);

            //vkFreeCommandBuffers(device, commandPool, commandBuffer);
            return pSignalSemaphore;
        }
    }

    public synchronized long submitCommands(CommandBuffer commandBuffer, VkQueue queue) {

        try(MemoryStack stack = stackPush()) {
            long fence = commandBuffer.fence;

            vkEndCommandBuffer(commandBuffer.handle);

            vkResetFences(Vulkan.getDevice(), commandBuffer.fence);

            VkSubmitInfo submitInfo = VkSubmitInfo.callocStack(stack);
            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
            submitInfo.pCommandBuffers(stack.pointers(commandBuffer.handle));

            vkQueueSubmit(queue, submitInfo, fence);
            //vkQueueWaitIdle(graphicsQueue);

            //vkFreeCommandBuffers(device, commandPool, commandBuffer);
            return fence;
        }
    }

    public void addToAvailable(CommandBuffer commandBuffer) {
        this.availableCmdBuffers.add(commandBuffer);
    }

    public void cleanUp() {
        for(CommandBuffer commandBuffer : commandBuffers) {
            vkDestroyFence(Vulkan.getDevice(), commandBuffer.fence, null);
        }
        vkResetCommandPool(Vulkan.getDevice(), id, VK_COMMAND_POOL_RESET_RELEASE_RESOURCES_BIT);
        vkDestroyCommandPool(Vulkan.getDevice(), id, null);
    }

    public class CommandBuffer {
        VkCommandBuffer handle;
        long fence;
        boolean submitted;
        boolean recording;

        public CommandBuffer(VkCommandBuffer handle, long fence) {
            this.handle = handle;
            this.fence = fence;
        }

        public VkCommandBuffer getHandle() {
            return handle;
        }

        public long getFence() {
            return fence;
        }

        public boolean isSubmitted() {
            return submitted;
        }

        public boolean isRecording() {
            return recording;
        }

        public void reset() {
            this.submitted = false;
            this.recording = false;
            addToAvailable(this);
        }
    }
}
