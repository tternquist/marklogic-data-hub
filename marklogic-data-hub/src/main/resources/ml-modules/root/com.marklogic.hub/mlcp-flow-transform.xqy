xquery version "1.0-ml";

module namespace mlcpFlow = "http://marklogic.com/data-hub/mlcp-flow-transform";

import module namespace config = "http://marklogic.com/data-hub/config"
  at "/com.marklogic.hub/lib/config.xqy";

import module namespace debug = "http://marklogic.com/data-hub/debug"
  at "/com.marklogic.hub/lib/debug-lib.xqy";

import module namespace flow = "http://marklogic.com/data-hub/flow-lib"
  at "/com.marklogic.hub/lib/flow-lib.xqy";

import module namespace hul = "http://marklogic.com/data-hub/hub-utils-lib"
  at "/com.marklogic.hub/lib/hub-utils-lib.xqy";

import module namespace perf = "http://marklogic.com/data-hub/perflog-lib"
  at "/com.marklogic.hub/lib/perflog-lib.xqy";

import module namespace trace = "http://marklogic.com/data-hub/trace"
  at "/com.marklogic.hub/lib/trace-lib.xqy";

declare namespace hub = "http://marklogic.com/data-hub";

declare option xdmp:mapping "false";

declare function mlcpFlow:transform(
  $content as map:map,
  $context as map:map
) as map:map*
{
  debug:dump-env("mlcpFlow:transform"),

  let $uri := map:get($content, "uri")
  return
    perf:log('mlcp-flow-transform(' || $uri || ')', function() {
      let $params := map:new((
        for $pair in map:get($context, 'transform_param') ! fn:tokenize(., ",")
        let $parts := fn:tokenize($pair, "=")
        return
          map:entry($parts[1], $parts[2])
      ))

      let $flow := flow:get-flow(
        map:get($params, 'entity'),
        map:get($params, 'flow'),
        map:get($params, 'flowType')
      )

      let $_ :=
        if ($flow) then ()
        else
          fn:error(xs:QName("MISSING_FLOW"), "The specified flow " || map:get($params, "flow") || " is missing.")

      (: configure the options :)
      let $options as map:map := (
        map:get($params, "options") ! xdmp:unquote(.)/object-node(),
        map:map()
      )[1]
      let $_ := flow:set-default-options($options, $flow)

      (: this can throw, but we want MLCP to know about problems, so let it :)
      let $envelope := mlcpFlow:run-flow(
        map:get($params, "jobId"), $flow, $uri, map:get($content, "value"), $options
      )
      let $_ := map:put($content, "value", $envelope)
      return
        $content
    })
};

declare function mlcpFlow:run-flow(
  $jobId, $flow, $uri, $content, $options)
{
  (: mlcp in runs in update mode :)
  xdmp:eval('
    import module namespace flow = "http://marklogic.com/data-hub/flow-lib"
      at "/com.marklogic.hub/lib/flow-lib.xqy";

    import module namespace trace = "http://marklogic.com/data-hub/trace"
      at "/com.marklogic.hub/lib/trace-lib.xqy";

    declare variable $jobId external;
    declare variable $flow external;
    declare variable $uri external;
    declare variable $content external;
    declare variable $options external;

    let $envelope := flow:run-flow($jobId, $flow, $uri, $content, $options)

    (: write trace for imaginary writer :)
    let $_ := (
      trace:set-plugin-label("rest builtin writer"),
      trace:reset-plugin-input(),
      trace:set-plugin-input("envelope", $envelope),
      trace:plugin-trace((), xs:dayTimeDuration("PT0S"))
    )
    let $_ := trace:write-trace()
    return
      $envelope
  ',
  map:new((
    map:entry("jobId", $jobId),
    map:entry("flow", $flow),
    map:entry("uri", $uri),
    map:entry("content", $content),
    map:entry("options", $options)
  )))
};
