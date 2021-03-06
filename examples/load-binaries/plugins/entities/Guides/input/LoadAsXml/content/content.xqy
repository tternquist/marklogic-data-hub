xquery version "1.0-ml";

module namespace plugin = "http://marklogic.com/data-hub/plugins";

declare namespace envelope = "http://marklogic.com/data-hub/envelope";

declare option xdmp:mapping "false";

(:~
 : Create Content Plugin
 :
 : @param $id          - the identifier returned by the collector
 : @param $raw-content - the raw content being loaded.
 : @param $options     - a map containing options. Options are sent from Java
 :
 : @return - your transformed content
 :)
declare function plugin:create-content(
  $id as xs:string,
  $raw-content as node()?,
  $options as map:map) as node()?
{
  (: name the binary uri with a pdf extension :)
  let $binary-uri := fn:replace($id, ".xml", ".pdf")

  (: stash the binary uri in the options map for later:)
  let $_ := map:put($options, 'binary-uri', $binary-uri)

  (: save the incoming binary as a pdf :)
  return
    xdmp:document-insert($binary-uri, $raw-content),

  (: extract the contents of the pdf and return them
   : as the content for the envelope
   :)
  xdmp:document-filter($raw-content)
};
