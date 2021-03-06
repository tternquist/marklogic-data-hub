xquery version "1.0-ml";

(: Your plugin must be in this namespace for the DHF to recognize it:)
module namespace plugin = "http://marklogic.com/data-hub/plugins";

(:
 : This module exposes helper functions to make your life easier
 : See documentation at:
 : https://github.com/marklogic/marklogic-data-hub/wiki/dhf-lib
 :)
import module namespace dhf = "http://marklogic.com/dhf"
at "/com.marklogic.hub/dhf.xqy";

(: include modules to construct various parts of the envelope :)
import module namespace content = "http://marklogic.com/data-hub/plugins" at "content.xqy";
import module namespace headers = "http://marklogic.com/data-hub/plugins" at "headers.xqy";
import module namespace triples = "http://marklogic.com/data-hub/plugins" at "triples.xqy";
import module namespace extra = "http://marklogic.com/data-hub/plugins" at "extra-plugin.xqy";

(: include the writer module which persists your envelope into MarkLogic :)
import module namespace writer = "http://marklogic.com/data-hub/plugins" at "writer.xqy";

declare option xdmp:mapping "false";

(:~
 : Plugin Entry point
 :
 : @param $id          - the identifier returned by the collector
 : @param $options     - a map containing options. Options are sent from Java
 :
 :)
declare function plugin:main(
  $id as xs:string,
  $options as map:map)
{
  let $content-context := dhf:content-context()
  let $content := dhf:run($content-context, function() {
    content:create-content($id, $options)
  })

  let $header-context := dhf:headers-context($content)
  let $headers := dhf:run($header-context, function() {
    headers:create-headers($id, $content, $options)
  })

  let $triple-context := dhf:triples-context($content, $headers)
  let $triples := dhf:run($triple-context, function() {
    triples:create-triples($id, $content, $headers, $options)
  })

  let $_ :=
    if (map:get($options, "mainGoBoom") eq fn:true() and $id = ("/input-2.json", "/input-2.xml")) then
      fn:error(xs:QName("MAIN-BOOM"), "I BLEW UP")
    else ()

  let $_ :=
    if (map:get($options, "extraPlugin") eq fn:true()) then
      let $extra-context := dhf:context("extraPlugin")
      let $_ := dhf:run($extra-context, function() {
        extra:do-something-extra($id, $options)
      })
      return ()
    else ()

  let $envelope := dhf:make-envelope($content, $headers, $triples, map:get($options, "dataFormat"))
  return
  (: explain: needed to call this way for static analysis :)
    dhf:run-writer(xdmp:function(xs:QName("writer:write")), $id, $envelope, $options)
};
