/*
 *  LanguageTool, a natural language style checker
 *  * Copyright (C) 2018 Fabian Richter
 *  *
 *  * This library is free software; you can redistribute it and/or
 *  * modify it under the terms of the GNU Lesser General Public
 *  * License as published by the Free Software Foundation; either
 *  * version 2.1 of the License, or (at your option) any later version.
 *  *
 *  * This library is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  * Lesser General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU Lesser General Public
 *  * License along with this library; if not, write to the Free Software
 *  * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 *  * USA
 *
 */

package org.languagetool.rules;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.SSLException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Streams;
import com.google.common.util.concurrent.ListenableFuture;

import io.grpc.*;
import io.grpc.internal.DnsNameResolverProvider;
import org.jetbrains.annotations.Nullable;
import org.languagetool.AnalyzedSentence;
import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.Tag;
import org.languagetool.rules.ml.MLServerGrpc;
import org.languagetool.rules.ml.MLServerGrpc.MLServerFutureStub;
import org.languagetool.rules.ml.MLServerProto;
import org.languagetool.rules.ml.MLServerProto.MatchResponse;
import org.languagetool.tools.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;

/**
 * Base class fur rules running on external servers;
 * see gRPC service definition in languagetool-core/src/main/proto/ml_server.proto
 *
 * See #create(Language, ResourceBundle, RemoteRuleConfig, boolean, String, String, Map)  for an easy way to add rules; return rule in Language::getRelevantRemoteRules
 * add it like this:
  <pre>
   public List&lt;Rule&gt; getRelevantRemoteRules(ResourceBundle messageBundle, List&lt;RemoteRuleConfig&gt; configs, GlobalConfig globalConfig, UserConfig userConfig, Language motherTongue, List&lt;Language&gt; altLanguages) throws IOException {
     List&lt;Rule&gt; rules = new ArrayList&lt;&gt;(super.getRelevantRemoteRules(
     messageBundle, configs, globalConfig, userConfig, motherTongue, altLanguages));
     Rule exampleRule = GRPCRule.create(messageBundle,
       RemoteRuleConfig.getRelevantConfig("EXAMPLE_ID", configs),
      "EXAMPLE_ID", "example_rule_id",
      Collections.singletonMap("example_match_id", "example_rule_message"));
     rules.add(exampleRule);
     return rules;
   }
  </pre>
 */
public abstract class GRPCRule extends RemoteRule {
  public static final String CONFIG_TYPE = "grpc";


  private static final Logger logger = LoggerFactory.getLogger(GRPCRule.class);
  private static final int DEFAULT_BATCH_SIZE = 8;
  public static final Pattern WHITESPACE_REGEX = Pattern.compile("[\u00a0\u202f\ufeff\ufffd]");
  private static final String DEFAULT_DESCRIPTION = "INTERNAL - dynamically loaded rule supported by remote server";
  /*TODO Delete this temporal fix as this is for speeding up execution for too long sentences*/

  public static String cleanID(String id, Language lang) {
    return StringTools.toId(id, lang);
  }
  /**
   * Internal rule to create rule matches with IDs based on Match Sub-IDs
   */
  public static class GRPCSubRule extends Rule {
    private final String matchId;
    private final String description;

    GRPCSubRule(MLServerProto.Match match, String description, Language lang) {
      String ruleId = match.getId();
      String subId = match.getSubId();
      if (subId != null && !subId.trim().isEmpty()) {
        this.matchId = cleanID(ruleId, lang) + "_" + cleanID(subId, lang);
      } else {
        this.matchId = cleanID(ruleId, lang);
      }
      this.description = description;
      setTags(match.getRule().getTagsList().stream().map(t -> Tag.valueOf(t.name())).collect(Collectors.toList()));
    }

    @Override
    public String getId() {
      return matchId;
    }

    @Override
    public String getDescription() {
      return this.description;
    }

    @Override
    public RuleMatch[] match(AnalyzedSentence sentence) throws IOException {
      throw new UnsupportedOperationException();
    }

  }

  public static class Connection {
    final ManagedChannel channel;
    final MLServerFutureStub stub;

    public static ManagedChannel getManagedChannel(String host, int port, boolean useSSL, @Nullable String clientPrivateKey, @Nullable String clientCertificate, @Nullable String rootCertificate) throws SSLException {
      NettyChannelBuilder channelBuilder;
      if (host.startsWith("dns://")) {
        channelBuilder = NettyChannelBuilder.forTarget(host + ":" + port);
        channelBuilder.defaultLoadBalancingPolicy("round_robin");
        NameResolverRegistry.getDefaultRegistry().register(new DnsNameResolverProvider());
      } else {
        channelBuilder = NettyChannelBuilder.forAddress(host, port);
      }
      if (useSSL) {
        SslContextBuilder sslContextBuilder = GrpcSslContexts.forClient();
        if (rootCertificate != null) {
          sslContextBuilder.trustManager(new File(rootCertificate));
        }
        if (clientCertificate != null && clientPrivateKey != null) {
          sslContextBuilder.keyManager(new File(clientCertificate), new File(clientPrivateKey));
        }
        channelBuilder = channelBuilder.negotiationType(NegotiationType.TLS).sslContext(sslContextBuilder.build());
      } else {
        channelBuilder = channelBuilder.usePlaintext();
      }
      return channelBuilder.build();
    }

    Connection(RemoteRuleConfig serviceConfiguration) throws SSLException {
      String host = serviceConfiguration.getUrl();
      int port = serviceConfiguration.getPort();
      boolean ssl = Boolean.parseBoolean(serviceConfiguration.getOptions().getOrDefault("secure", "false"));
      String key = serviceConfiguration.getOptions().get("clientKey");
      String cert = serviceConfiguration.getOptions().get("clientCertificate");
      String ca = serviceConfiguration.getOptions().get("rootCertificate");
      this.channel = getManagedChannel(host, port, ssl, key, cert, ca);
      this.stub = MLServerGrpc.newFutureStub(channel);
    }

    private void shutdown() {
      if (channel != null) {
        channel.shutdownNow();
      }
    }
  }

  private static final LoadingCache<RemoteRuleConfig, Connection> servers =
    CacheBuilder.newBuilder().build(CacheLoader.from(serviceConfiguration -> {
      if (serviceConfiguration == null) {
        throw new IllegalArgumentException("No configuration for connection given");
      }
      try {
        return new Connection(serviceConfiguration);
      } catch (SSLException e) {
        throw new RuntimeException(e);
      }
    }));

  static {
    shutdownRoutines.add(() -> servers.asMap().values().forEach(Connection::shutdown));
  }

  private final Connection conn;
  private final int batchSize;
  private final boolean sendAnalyzedData;
  private int maxSentenceLength;

  public GRPCRule(Language language, ResourceBundle messages, RemoteRuleConfig config, boolean inputLogging) {
    super(language, messages, config, inputLogging);

    this.maxSentenceLength = Integer.parseInt(config.getOptions().getOrDefault("maxSentenceLength", String.valueOf(Integer.MAX_VALUE)));
    sendAnalyzedData = config.getOptions()
      .getOrDefault("analyzed", "false")
      .equalsIgnoreCase("true");
    this.batchSize = Integer.parseInt(config.getOptions().getOrDefault("batchSize",
                                                                       String.valueOf(DEFAULT_BATCH_SIZE)));
    synchronized (servers) {
      Connection conn = null;
        try {
          conn = servers.get(serviceConfiguration);
        } catch (Exception e) {
          logger.error("Could not connect to remote service at " + serviceConfiguration, e);
        }
      this.conn = conn;
    }
  }

  protected static class MLRuleRequest extends RemoteRule.RemoteRequest {
    final List<MLServerProto.MatchRequest> requests;
    final List<AnalyzedSentence> sentences;
    final Long textSessionId;

    public MLRuleRequest(List<MLServerProto.MatchRequest> requests, List<AnalyzedSentence> sentences, Long textSessionId) {
      this.requests = requests;
      this.sentences = sentences;
      this.textSessionId = textSessionId;
    }
  }


  protected static class AnalyzedMLRuleRequest extends RemoteRule.RemoteRequest {
    final List<MLServerProto.AnalyzedMatchRequest> requests;
    final List<AnalyzedSentence> sentences;

    public AnalyzedMLRuleRequest(List<MLServerProto.AnalyzedMatchRequest> requests, List<AnalyzedSentence> sentences) {
      this.requests = requests;
      this.sentences = sentences;
    }
  }

  @Override
  protected RemoteRule.RemoteRequest prepareRequest(List<AnalyzedSentence> sentences, @Nullable Long textSessionId) {
    List<Long> ids = Collections.emptyList();
    // TODO this is a temp fix to avoid sending too long sentences to the server
    List<AnalyzedSentence> filteredSentences = sentences.stream()
      .filter(s -> s.getText().length() <= maxSentenceLength)
      .collect(Collectors.toList());

    if (textSessionId != null) {
      ids = Collections.nCopies(filteredSentences.size(), textSessionId);
    }
    if (sendAnalyzedData) {
      List<MLServerProto.AnalyzedMatchRequest> requests = new ArrayList<>();

      for (int offset = 0; offset < filteredSentences.size(); offset += batchSize) {
        MLServerProto.AnalyzedMatchRequest req = MLServerProto.AnalyzedMatchRequest.newBuilder()
          .addAllSentences(filteredSentences
            .subList(offset, Math.min(filteredSentences.size(), offset + batchSize))
            .stream().map(GRPCUtils::toGRPC).collect(Collectors.toList()))
          .setInputLogging(inputLogging)
          .addAllTextSessionID(textSessionId != null ?
            ids.subList(offset, Math.min(filteredSentences.size(), offset + batchSize))
            : Collections.emptyList())
          .build();
        requests.add(req);
      }
      return new AnalyzedMLRuleRequest(requests, filteredSentences);
    } else {
      List<MLServerProto.MatchRequest> requests = new ArrayList<>();

      for (int offset = 0; offset < filteredSentences.size(); offset += batchSize) {
        List<String> text = filteredSentences.stream().map(AnalyzedSentence::getText).map(s -> {
          if (whitespaceNormalisation) {
            // non-breaking space can be treated as normal space
            return WHITESPACE_REGEX.matcher(s).replaceAll(" ");
          } else {
            return s;
          }
        }).collect(Collectors.toList());
        MLServerProto.MatchRequest req = MLServerProto.MatchRequest.newBuilder()
          .addAllSentences(text.subList(offset, Math.min(text.size(), offset + batchSize)))
          .setInputLogging(inputLogging)
          .addAllTextSessionID(textSessionId != null ?
                              ids.subList(offset, Math.min(text.size(), offset + batchSize))
                              : Collections.emptyList())
          .build();
        requests.add(req);
      }
      if (requests.size() > 1) {
        logger.debug("Split {} sentences into {} requests for {}", filteredSentences.size(), requests.size(), getId());
      }
      return new MLRuleRequest(requests, filteredSentences, textSessionId);
    }
  }

  @Nullable
  private static String nonEmpty(String s) {
    if (s.isEmpty()) {
      return null;
    }
    return s;
  }

  @Override
  protected Callable<RemoteRuleResult> executeRequest(RemoteRequest requestArg, long timeoutMilliseconds) throws TimeoutException {
    return () -> {
      MLRuleRequest reqArgs = (MLRuleRequest) requestArg;
      // NOTE: disabled for now, don't want to run this in the nightly diff
      boolean noRegression = Boolean.parseBoolean(serviceConfiguration.getOptions().getOrDefault("no-regression", "false"));
      if (noRegression && reqArgs.textSessionId != null && (reqArgs.textSessionId == -1 || reqArgs.textSessionId == -2)) {
        return new RemoteRuleResult(false, true, Collections.emptyList(), reqArgs.sentences);
      }

      List<AnalyzedSentence> sentences;
      List<ListenableFuture<MatchResponse>> futures = new ArrayList<>();
      List<MatchResponse> responses = new ArrayList<>();
      try {
        if (sendAnalyzedData) {
          AnalyzedMLRuleRequest reqData = (AnalyzedMLRuleRequest) requestArg;
          sentences = reqData.sentences;

          for (MLServerProto.AnalyzedMatchRequest req : reqData.requests) {
            if (timeoutMilliseconds > 0) {
              logger.debug("Deadline for rule {}: {}ms", getId(), timeoutMilliseconds);
              futures.add(conn.stub
                .withDeadlineAfter(timeoutMilliseconds, TimeUnit.MILLISECONDS)
                .matchAnalyzed(req));
            } else {
              futures.add(conn.stub.matchAnalyzed(req));
            }
          }
        } else {
          MLRuleRequest reqData = (MLRuleRequest) requestArg;
          sentences = reqData.sentences;

          for (MLServerProto.MatchRequest req : reqData.requests) {
            if (timeoutMilliseconds > 0) {
              logger.debug("Deadline for rule {}: {}ms", getId(), timeoutMilliseconds);
              futures.add(conn.stub
                .withDeadlineAfter(timeoutMilliseconds, TimeUnit.MILLISECONDS)
                .match(req));
            } else {
              futures.add(conn.stub.match(req));
            }
          }
        }
        // TODO: handle partial failures
        for (ListenableFuture<MatchResponse> res : futures) {
          responses.add(res.get());
        }
      } catch (StatusRuntimeException e) {
        if (e.getStatus().getCode() == Status.DEADLINE_EXCEEDED.getCode()) {
          throw new TimeoutException(e.getMessage());
        } else {
          throw e;
        }
      } catch (InterruptedException | ExecutionException e) {
        throw new TimeoutException(e + Objects.toString(e.getMessage()));
      }

      List<RuleMatch> matches = getRuleMatches(sentences, responses);
      RemoteRuleResult result = new RemoteRuleResult(true, true, matches, sentences);
      return result;
    };
  }

  private List<RuleMatch> getRuleMatches(List<AnalyzedSentence> sentences, List<MatchResponse> responses) {
    BiFunction<MLServerProto.MatchList, AnalyzedSentence, Stream<RuleMatch>> createMatch = (matchList, sentence) -> matchList.getMatchesList().stream().map(match -> {
        String description = match.getRuleDescription();
        if (description == null || description.isEmpty()) {
          description = this.getDescription();
          if (description == null || description.isEmpty()) {
            throw new RuntimeException("Missing description for rule with ID " + match.getId() + "_" + match.getSubId());
          }
        }
        GRPCSubRule subRule = new GRPCSubRule(match, description, ruleLanguage);
        String message = match.getMatchDescription();
        String shortMessage = match.getMatchShortDescription();
        if (message == null || message.isEmpty()) {
          message = getMessage(match, sentence);
        }
        if (message == null || message.isEmpty()) {
          throw new RuntimeException("Missing message for match with ID " + subRule.getId());
        }
        int start = match.getOffset();
        int end = start + match.getLength();
        RuleMatch m = new RuleMatch(subRule, sentence,
          start, end,
          message, shortMessage);
        if (!match.getUrl().isEmpty()) {
          try {
            m.setUrl(new URL(match.getUrl()));
          } catch (MalformedURLException e) {
            logger.warn("Got invalid URL from GRPC rule {}: {}", this, e);
          }
        }
        m.setAutoCorrect(match.getAutoCorrect());
        // suggestedReplacements should override suggestions
        if (match.getSuggestedReplacementsList().isEmpty()) {
          m.setSuggestedReplacements(match.getSuggestionsList());
        } else {
          m.setSuggestedReplacementObjects(match.getSuggestedReplacementsList().stream().map(s -> {
            SuggestedReplacement repl = new SuggestedReplacement(
              s.getReplacement(), nonEmpty(s.getDescription()), nonEmpty(s.getSuffix()));
            if (s.getConfidence() > 0.0) {
              repl.setConfidence(s.getConfidence());
            }
            return repl;
          }).collect(Collectors.toList()));
        }
        return m;
      }
    );

    List<RuleMatch> matches = Streams.zip(
      responses.stream()
        .flatMap(res -> res.getSentenceMatchesList().stream()),
      sentences.stream(),
      createMatch)
      .flatMap(Function.identity()).collect(Collectors.toList());
    return matches;
  }

  /**
   * messages can be provided by the ML server or the Java client
   * fill them in here or leave this empty if the server takes care of it
   */
  protected abstract String getMessage(MLServerProto.Match match, AnalyzedSentence sentence);

  @Override
  protected RemoteRuleResult fallbackResults(RemoteRule.RemoteRequest request) {
    MLRuleRequest req = (MLRuleRequest) request;
    return new RemoteRuleResult(false, false, Collections.emptyList(), req.sentences);
  }

  /**
   * Helper method to create instances of RemoteMLRule
   * @param language rule language
   * @param messages for i18n; = JLanguageTool.getMessageBundle(lang)
   * @param config configuration for remote rule server;
   *               options: secure, clientKey, clientCertificate, rootCertificate
                   use RemoteRuleConfig.getRelevantConfig(id, configs)
                   to load this in Language::getRelevantRemoteRules
   * @param id ID of rule
   * @param descriptionKey key in MessageBundle.properties for rule description
   * @param messagesByID mapping match.sub_id -&gt; key in MessageBundle.properties for RuleMatch's message
   * @return instance of RemoteMLRule
   */
  public static GRPCRule create(Language language, ResourceBundle messages, RemoteRuleConfig config, boolean inputLogging,
                                String id, String descriptionKey, Map<String, String> messagesByID) {
    return new GRPCRule(language, messages, config, inputLogging) {


      @Override
      protected String getMessage(MLServerProto.Match match, AnalyzedSentence sentence) {
        return messages.getString(messagesByID.get(match.getSubId()));
      }

      @Override
      public String getDescription() {
        return messages.getString(descriptionKey);
      }
    };
  }

  /**
   * Helper method to create instances of RemoteMLRule
   * @param language rule language
   * @param config configuration for remote rule server;
   *               options: secure, clientKey, clientCertificate, rootCertificate
                   use RemoteRuleConfig.getRelevantConfig(id, configs)
                   to load this in Language::getRelevantRemoteRules
   * @param id ID of rule
   * @param description rule description
   * @param messagesByID mapping match.sub_id to RuleMatch's message
   * @return instance of RemoteMLRule
   */
  public static GRPCRule create(Language language, RemoteRuleConfig config, boolean inputLogging,
                                String id, String description, Map<String, String> messagesByID) {
    return new GRPCRule(language, JLanguageTool.getMessageBundle(), config, inputLogging) {
      @Override
      protected String getMessage(MLServerProto.Match match, AnalyzedSentence sentence) {
        return messagesByID.get(match.getSubId());
      }
      @Override
      public String getDescription() {
        return description;
      }
    };
  }

  public static List<GRPCRule> createAll(Language language, List<RemoteRuleConfig> configs, boolean inputLogging, String prefix, String defaultDescription) {
    return configs.stream()
      .filter(cfg -> cfg.getRuleId().startsWith(prefix))
      .map(cfg -> create(language, cfg, inputLogging, cfg.getRuleId(), defaultDescription, Collections.emptyMap()))
      .collect(Collectors.toList());
  }

  public static List<GRPCRule> createAll(Language language, List<RemoteRuleConfig> configs, boolean inputLogging) {
    return configs.stream()
      .filter(RemoteRuleConfig.isRelevantConfig(CONFIG_TYPE, language))
      .map(cfg -> create(language, cfg, inputLogging, cfg.getRuleId(), DEFAULT_DESCRIPTION, Collections.emptyMap()))
      .collect(Collectors.toList());
  }
}
