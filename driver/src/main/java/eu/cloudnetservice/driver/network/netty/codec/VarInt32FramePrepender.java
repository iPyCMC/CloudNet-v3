/*
 * Copyright 2019-2023 CloudNetService team & contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.cloudnetservice.driver.network.netty.codec;

import eu.cloudnetservice.driver.network.netty.NettyUtil;
import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.MessageToMessageEncoder;
import java.util.List;
import lombok.NonNull;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class VarInt32FramePrepender extends MessageToMessageEncoder<Buffer> {

  public static final VarInt32FramePrepender INSTANCE = new VarInt32FramePrepender();

  /**
   * {@inheritDoc}
   */
  @Override
  protected void encode(@NonNull ChannelHandlerContext ctx, @NonNull Buffer msg, @NonNull List<Object> out) {
    // first write the buffer that contains the length of the following buffer
    var length = msg.readableBytes();
    var lengthBuffer = ctx.bufferAllocator().allocate(NettyUtil.varIntBytes(length));
    NettyUtil.writeVarInt(lengthBuffer, length);

    // put both the length buffer and then the actual message buffer into the output list
    out.add(lengthBuffer);
    out.add(msg.split());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSharable() {
    return true;
  }
}
