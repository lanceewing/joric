export default {
    
    async fetch(request) {
        
        const corsHeaders = {
            "Access-Control-Allow-Origin": "https://oric.games",
            "Access-Control-Allow-Methods": "GET,HEAD,POST,OPTIONS",
            "Access-Control-Max-Age": "86400",
            "Vary": "Origin",
        };

        async function handleRequest(request) {
            const url = new URL(request.url);
            let targetUrl = url.searchParams.get("url");
            
            // If the target url is not present, return 400.
            if (!targetUrl) {
                return new Response("Missing target URL.", { status: 400 });
            }

            if ((url.origin == "https://oric.games") && 
                (targetUrl.startsWith("https://www.defence-force.org/") || 
                 targetUrl.startsWith("https://cdn.oric.org/"))) {

                // Rewrite request to point to target URL. This also makes the request 
                // mutable so you can add the correct Origin header to make the API server
                // think that this request is not cross-site.
                request = new Request(targetUrl, request);
                request.headers.set("Origin", new URL(targetUrl).origin);
                
                // Perform request to destination URL.
                let response = await fetch(request);
                
                // Recreate the response so you can modify the headers
                response = new Response(response.body, response);
                
                // Set CORS headers
                response.headers.set("Access-Control-Allow-Origin", "https://oric.games");
    
                // Append to/Add Vary header so browser will cache response correctly
                response.headers.append("Vary", "Origin");
    
                return response;
                
            } else {
                // Either the Origin or target URL domain is not supported.
                return new Response(
                    "Forbidden\n",
                    {
                        status: 403,
                        statusText: 'Forbidden',
                        headers: {
                            "Content-Type": "text/html"
                        }
                    }
                );
            }
        }

        async function handleOptions(request) {
            if (
                request.headers.get("Origin") !== null &&
                request.headers.get("Access-Control-Request-Method") !== null &&
                request.headers.get("Access-Control-Request-Headers") !== null
            ) {
                // Handle CORS preflight requests.
                return new Response(null, {
                    headers: {
                        ...corsHeaders,
                        "Access-Control-Allow-Headers": request.headers.get(
                            "Access-Control-Request-Headers",
                        ),
                    },
                });
            } else {
                // Handle standard OPTIONS request.
                return new Response(null, {
                    headers: {
                        Allow: "GET, HEAD, POST, OPTIONS",
                    },
                });
            }
        }

        if (request.method === "OPTIONS") {
            // Handle CORS preflight requests
            return handleOptions(request);
        }
        else if (
            request.method === "GET" ||
            request.method === "HEAD" ||
            request.method === "POST") {
                
            // Handle requests to the destination server
            return handleRequest(request);
        } 
        else {
            // A request method that is not supported.
            return new Response(null, {
                status: 405,
                statusText: "Method Not Allowed",
            });
        }
        
    },
};
