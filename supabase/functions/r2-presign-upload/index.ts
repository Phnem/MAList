import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "npm:@supabase/supabase-js"
import { S3Client, PutObjectCommand } from "npm:@aws-sdk/client-s3"
import { getSignedUrl } from "npm:@aws-sdk/s3-request-presigner"
import { corsHeaders } from "../_shared/cors.ts"

const s3Client = new S3Client({
  region: "auto",
  endpoint: Deno.env.get("R2_ENDPOINT")!,
  credentials: {
    accessKeyId: Deno.env.get("R2_ACCESS_KEY_ID")!,
    secretAccessKey: Deno.env.get("R2_SECRET_ACCESS_KEY")!,
  },
})

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }
  
  try {
    const authHeader = req.headers.get('Authorization')
    if (!authHeader) {
      return new Response(JSON.stringify({ error: 'Missing auth header' }), { status: 401, headers: corsHeaders })
    }

    const supabase = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_ANON_KEY') ?? '',
      { global: { headers: { Authorization: authHeader } } }
    )
    
    const { data: { user }, error: userError } = await supabase.auth.getUser()
    if (userError || !user) {
      return new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 401, headers: corsHeaders })
    }
    const userId = user.id
    
    const body = await req.json()
    const { attachmentId, filename, mimeType, sizeBytes } = body
    
    if (!attachmentId || !filename) {
      return new Response(JSON.stringify({ error: 'Missing attachmentId or filename' }), { status: 400, headers: corsHeaders })
    }

    const bucketName = Deno.env.get("R2_BUCKET_NAME")!
    const r2Key = `${userId}/${attachmentId}.jpg`

    const command = new PutObjectCommand({
      Bucket: bucketName,
      Key: r2Key,
      ContentType: mimeType || 'application/octet-stream',
      ContentLength: sizeBytes,
    })

    const uploadUrl = await getSignedUrl(s3Client, command, { expiresIn: 3600 })

    return new Response(
      JSON.stringify({ uploadUrl, r2Key, attachmentId, expiresIn: 3600 }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" } },
    )
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    })
  }
})
