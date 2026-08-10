targetScope = 'resourceGroup'

@description('Azure region for every production resource.')
param location string

@description('Globally unique ACR name. The adapter requires a rotrackproduction* name.')
@minLength(5)
@maxLength(50)
param acrName string

@description('Monthly resource-group budget amount in the subscription currency.')
@minValue(1)
param budgetAmount int

@description('UTC period start, at 00:00:00Z and no more than 366 days before the end.')
param budgetStartDate string

@description('UTC period end, at 00:00:00Z and after budgetStartDate.')
param budgetEndDate string

@description('Budget notification recipients. Keep populated values outside source control.')
param budgetAlertEmails array

var workspaceName = 'rotrack-production-logs'
var managedEnvironmentName = 'rotrack-production-env'
var identityName = 'rotrack-api-production-identity'
var acrPullRoleDefinitionId = '7f951dda-4ed3-4680-a7ca-43fe172d538d'

resource workspace 'Microsoft.OperationalInsights/workspaces@2022-10-01' = {
  name: workspaceName
  location: location
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: 30
    workspaceCapping: {
      dailyQuotaGb: json('0.1')
    }
    features: {
      enableLogAccessUsingOnlyResourcePermissions: true
    }
  }
}

resource registry 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: acrName
  location: location
  sku: {
    name: 'Basic'
  }
  properties: {
    adminUserEnabled: false
    publicNetworkAccess: 'Enabled'
  }
}

resource pullIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: identityName
  location: location
}

resource acrPullRoleAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(registry.id, pullIdentity.id, acrPullRoleDefinitionId)
  scope: registry
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', acrPullRoleDefinitionId)
    principalId: pullIdentity.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

resource managedEnvironment 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: managedEnvironmentName
  location: location
  properties: {
    workloadProfiles: [
      {
        name: 'Consumption'
        workloadProfileType: 'Consumption'
      }
    ]
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: workspace.properties.customerId
        sharedKey: workspace.listKeys().primarySharedKey
      }
    }
  }
}

resource budget 'Microsoft.Consumption/budgets@2023-05-01' = {
  name: 'rotrack-production-budget'
  properties: {
    category: 'Cost'
    amount: budgetAmount
    timeGrain: 'Monthly'
    timePeriod: {
      startDate: budgetStartDate
      endDate: budgetEndDate
    }
    notifications: {
      actual50: {
        enabled: true
        operator: 'GreaterThan'
        threshold: 50
        thresholdType: 'Actual'
        contactEmails: budgetAlertEmails
        contactRoles: []
        contactGroups: []
      }
      actual80: {
        enabled: true
        operator: 'GreaterThan'
        threshold: 80
        thresholdType: 'Actual'
        contactEmails: budgetAlertEmails
        contactRoles: []
        contactGroups: []
      }
      actual100: {
        enabled: true
        operator: 'GreaterThan'
        threshold: 100
        thresholdType: 'Actual'
        contactEmails: budgetAlertEmails
        contactRoles: []
        contactGroups: []
      }
    }
  }
}

output registryLoginServer string = registry.properties.loginServer
output managedEnvironmentResourceId string = managedEnvironment.id
output pullIdentityResourceId string = pullIdentity.id
