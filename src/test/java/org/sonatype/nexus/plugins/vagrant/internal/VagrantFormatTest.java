package org.sonatype.nexus.plugins.vagrant.internal;

import org.sonatype.goodies.testsupport.TestSupport;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class VagrantFormatTest
    extends TestSupport
{
  @Test
  public void formatNameIsVagrant() {
    VagrantFormat format = new VagrantFormat();
    assertThat(format.getValue(), is("vagrant"));
  }

  @Test
  public void nameConstantMatchesValue() {
    assertThat(VagrantFormat.NAME, is("vagrant"));
    VagrantFormat format = new VagrantFormat();
    assertThat(format.getValue(), is(VagrantFormat.NAME));
  }
}
