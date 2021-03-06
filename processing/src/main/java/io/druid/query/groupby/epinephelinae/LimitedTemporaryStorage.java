/*
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Metamarkets licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.druid.query.groupby.epinephelinae;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.metamx.common.ISE;
import com.metamx.common.logger.Logger;

import java.io.Closeable;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An area for limited temporary storage on disk. Limits are checked when opening files and on all writes to those
 * files. Thread-safe.
 */
public class LimitedTemporaryStorage implements Closeable
{
  private static final Logger log = new Logger(LimitedTemporaryStorage.class);

  private final File storageDirectory;
  private final long maxBytesUsed;

  private final AtomicLong bytesUsed = new AtomicLong();
  private final Set<File> files = Sets.newTreeSet();

  private volatile boolean closed = false;

  public LimitedTemporaryStorage(File storageDirectory, long maxBytesUsed)
  {
    this.storageDirectory = storageDirectory;
    this.maxBytesUsed = maxBytesUsed;
  }

  public LimitedOutputStream createFile() throws IOException
  {
    if (bytesUsed.get() >= maxBytesUsed) {
      throwFullError();
    }

    synchronized (files) {
      if (closed) {
        throw new ISE("Closed");
      }

      if (!storageDirectory.exists() && !storageDirectory.mkdir()) {
        throw new IOException(String.format("Cannot create storageDirectory: %s", storageDirectory));
      }

      final File theFile = new File(storageDirectory, String.format("%08d.tmp", files.size()));
      final EnumSet<StandardOpenOption> openOptions = EnumSet.of(
          StandardOpenOption.CREATE_NEW,
          StandardOpenOption.WRITE
      );

      final FileChannel channel = FileChannel.open(theFile.toPath(), openOptions);
      files.add(theFile);
      return new LimitedOutputStream(theFile, Channels.newOutputStream(channel));
    }
  }

  public void delete(final File file)
  {
    synchronized (files) {
      if (files.contains(file)) {
        try {
          Files.delete(file.toPath());
        }
        catch (IOException e) {
          log.warn(e, "Cannot delete file: %s", file);
        }
        files.remove(file);
      }
    }
  }

  @Override
  public void close()
  {
    synchronized (files) {
      if (closed) {
        return;
      }
      closed = true;
      for (File file : ImmutableSet.copyOf(files)) {
        delete(file);
      }
      files.clear();
      if (storageDirectory.exists() && !storageDirectory.delete()) {
        log.warn("Cannot delete storageDirectory: %s", storageDirectory);
      }
    }
  }

  public class LimitedOutputStream extends FilterOutputStream
  {
    private final File file;

    private LimitedOutputStream(File file, OutputStream out)
    {
      super(out);
      this.file = file;
    }

    @Override
    public void write(int b) throws IOException
    {
      grab(1);
      super.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException
    {
      grab(b.length);
      super.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException
    {
      grab(len);
      super.write(b, off, len);
    }

    public File getFile()
    {
      return file;
    }

    private void grab(int n) throws IOException
    {
      if (bytesUsed.addAndGet(n) > maxBytesUsed) {
        throwFullError();
      }
    }

  }

  private void throwFullError() throws IOException
  {
    throw new IOException(String.format("Cannot write to disk, hit limit of %,d bytes.", maxBytesUsed));
  }
}
