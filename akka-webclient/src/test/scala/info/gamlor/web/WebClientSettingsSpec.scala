package info.gamlor.web

import org.scalatest.Spec
import org.scalatest.matchers.MustMatchers
import com.typesafe.config.ConfigFactory

/**
 * @author roman.stoffel@gamlor.info
 * @since 05.03.12
 */

class WebClientSettingsSpec extends Spec with MustMatchers {

  describe("The configuration") {

    it("has default settings") {

      var refCfg = WebClientSettings.createConfig(ConfigFactory.load())

      refCfg.getMaxTotalConnections must be(-1);
      refCfg.getMaxConnectionPerHost must be(-1);
      refCfg.getIdleConnectionInPoolTimeoutInMs must be(60 * 1000);
      refCfg.getIdleConnectionTimeoutInMs must be(60 * 1000);
      refCfg.getWebSocketIdleTimeoutInMs must be(15*60 * 1000);
      refCfg.getRequestTimeoutInMs must be(60 * 1000);
      refCfg.isRedirectEnabled must be(false);
      refCfg.getMaxRedirects must be(5);
      refCfg.isCompressionEnabled must be(false);
      refCfg.getUserAgent must be("NING/1.0");
      refCfg.getProxyServer must be(null);

    }

    it("loads app settings") {

      var refCfg = WebClientSettings.createConfig(ConfigFactory.load("fullconfig"))

      refCfg.getMaxTotalConnections must be(42);
      refCfg.getMaxConnectionPerHost must be(42);
      refCfg.getIdleConnectionInPoolTimeoutInMs must be(42 * 1000);
      refCfg.getIdleConnectionTimeoutInMs must be(42 * 1000);
      refCfg.getWebSocketIdleTimeoutInMs must be(42 * 1000);
      refCfg.getRequestTimeoutInMs must be(42 * 1000);
      refCfg.isRedirectEnabled must be(true);
      refCfg.getMaxRedirects must be(42);
      refCfg.isCompressionEnabled must be(true);
      refCfg.getUserAgent must be("42");

    }
  }

}
