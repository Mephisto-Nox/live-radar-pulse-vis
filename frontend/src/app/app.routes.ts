import { Routes } from '@angular/router';
import { LiveDashboardPage } from './live-dashboard-page';
import { PlaceholderPage } from './placeholder-page';

export const routes: Routes = [
	{ path: '', pathMatch: 'full', redirectTo: 'start' },
	{ path: 'start', component: PlaceholderPage, data: { title: 'Willkommen', section: 'START' } },
	{ path: 'dashboard', component: LiveDashboardPage },
	{ path: 'detail', component: PlaceholderPage, data: { title: 'Detailansicht', section: 'ANALYSE' } },
	{ path: 'settings', component: PlaceholderPage, data: { title: 'Einstellungen', section: 'SYSTEM' } },
	{ path: '**', redirectTo: 'start' },
];
