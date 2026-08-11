targetScope = 'resourceGroup'

@description('Azure region for the Container App.')
param location string

@description('Existing non-production ACR name created by foundation.bicep.')
@minLength(5)
@maxLength(50)
param acrName string

@description('Repository path inside ACR for the platform-neutral backend image.')
@minLength(1)
param imageRepository string = 'rotrack-api'

@description('Immutable OCI registry digest. The app is never deployed from a tag.')
param imageDigest string

@secure()
@description('JDBC URL with sslmode=verify-full and the injected CA path.')
param databaseUrl string

@secure()
param databaseUsername string

@secure()
param databasePassword string

@secure()
@description('Official provider CA PEM; materialized by the image entrypoint at runtime.')
param databaseCaCertificatePem string

@secure()
param supabaseJwksUri string

@secure()
param supabaseIssuerUri string

@description('Exact HTTPS Vercel Preview origin(s), comma-separated.')
@minLength(1)
param corsAllowedOrigins string

var managedEnvironmentName = 'rotrack-nonproduction-env'
var containerAppName = 'rotrack-api-nonproduction'
var identityName = 'rotrack-api-nonproduction-identity'

resource registry 'Microsoft.ContainerRegistry/registries@2023-07-01' existing = {
  name: acrName
}

resource pullIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' existing = {
  name: identityName
}

resource managedEnvironment 'Microsoft.App/managedEnvironments@2024-03-01' existing = {
  name: managedEnvironmentName
}

resource containerApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: containerAppName
  location: location
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${pullIdentity.id}': {}
    }
  }
  properties: {
    managedEnvironmentId: managedEnvironment.id
    workloadProfileName: 'Consumption'
    configuration: {
      activeRevisionsMode: 'Multiple'
      ingress: {
        external: true
        targetPort: 8080
        transport: 'http'
        allowInsecure: false
        traffic: [
          {
            latestRevision: true
            weight: 100
          }
        ]
      }
      registries: [
        {
          server: registry.properties.loginServer
          identity: pullIdentity.id
        }
      ]
      secrets: [
        {
          name: 'database-url'
          value: databaseUrl
        }
        {
          name: 'database-username'
          value: databaseUsername
        }
        {
          name: 'database-password'
          value: databasePassword
        }
        {
          name: 'database-ca-certificate-pem'
          value: databaseCaCertificatePem
        }
        {
          name: 'supabase-jwks-uri'
          value: supabaseJwksUri
        }
        {
          name: 'supabase-issuer-uri'
          value: supabaseIssuerUri
        }
      ]
    }
    template: {
      terminationGracePeriodSeconds: 30
      scale: {
        minReplicas: 0
        maxReplicas: 1
      }
      containers: [
        {
          name: 'api'
          image: '${registry.properties.loginServer}/${imageRepository}@${imageDigest}'
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: [
            {
              name: 'DATABASE_URL'
              secretRef: 'database-url'
            }
            {
              name: 'DATABASE_USERNAME'
              secretRef: 'database-username'
            }
            {
              name: 'DATABASE_PASSWORD'
              secretRef: 'database-password'
            }
            {
              name: 'DATABASE_CA_CERTIFICATE_PEM'
              secretRef: 'database-ca-certificate-pem'
            }
            {
              name: 'SUPABASE_JWKS_URI'
              secretRef: 'supabase-jwks-uri'
            }
            {
              name: 'SUPABASE_ISSUER_URI'
              secretRef: 'supabase-issuer-uri'
            }
            {
              name: 'PORT'
              value: '8080'
            }
            {
              name: 'DATABASE_CA_CERTIFICATE_PATH'
              value: '/tmp/rotrack-certs/supabase-db-ca.crt'
            }
            {
              name: 'DATABASE_CONNECTION_TIMEOUT_MS'
              value: '5000'
            }
            {
              name: 'DATABASE_POOL_VALIDATION_TIMEOUT_MS'
              value: '2000'
            }
            {
              name: 'DATABASE_MAXIMUM_POOL_SIZE'
              value: '5'
            }
            {
              name: 'DATABASE_MINIMUM_IDLE'
              value: '0'
            }
            {
              name: 'READINESS_CACHE_TTL'
              value: '5s'
            }
            {
              name: 'SUPABASE_JWT_AUDIENCE'
              value: 'authenticated'
            }
            {
              name: 'CORS_ALLOWED_ORIGINS'
              value: corsAllowedOrigins
            }
            {
              name: 'ROTRACK_MUTATION_RATE_LIMIT_REQUESTS'
              value: '30'
            }
            {
              name: 'ROTRACK_MUTATION_RATE_LIMIT_WINDOW'
              value: '1m'
            }
            {
              name: 'ROTRACK_MUTATION_RATE_LIMIT_MAX_KEYS'
              value: '10000'
            }
            {
              name: 'SERVER_SHUTDOWN'
              value: 'graceful'
            }
            {
              name: 'SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE'
              value: '25s'
            }
            {
              name: 'LOGGING_STRUCTURED_FORMAT_CONSOLE'
              value: 'ecs'
            }
            {
              name: 'ROTRACK_STRUCTURED_LOGGING_ENABLED'
              value: 'true'
            }
            {
              name: 'ROTRACK_LOGGING_ENVIRONMENT'
              value: 'production'
            }
            {
              name: 'ROTRACK_SERVICE_VERSION'
              value: imageDigest
            }
            {
              name: 'LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY'
              value: 'WARN'
            }
            {
              name: 'LOGGING_LEVEL_ORG_HIBERNATE_SQL'
              value: 'OFF'
            }
            {
              name: 'LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND'
              value: 'OFF'
            }
            {
              name: 'SPRING_JPA_SHOW_SQL'
              value: 'false'
            }
          ]
          probes: [
            {
              type: 'Liveness'
              httpGet: {
                path: '/api/v1/health'
                port: 8080
                scheme: 'HTTP'
              }
              initialDelaySeconds: 10
              periodSeconds: 10
              timeoutSeconds: 2
              failureThreshold: 3
            }
            {
              type: 'Readiness'
              httpGet: {
                path: '/api/v1/readiness'
                port: 8080
                scheme: 'HTTP'
              }
              initialDelaySeconds: 10
              periodSeconds: 10
              timeoutSeconds: 2
              failureThreshold: 3
            }
          ]
        }
      ]
    }
  }
}

output containerAppResourceId string = containerApp.id
output containerAppFqdn string = containerApp.properties.configuration.ingress.fqdn
output imageReference string = '${registry.properties.loginServer}/${imageRepository}@${imageDigest}'
output serviceVersion string = imageDigest
