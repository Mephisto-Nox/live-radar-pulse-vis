import { Routes } from '@angular/router';
import { LiveDashboardPage } from './live-dashboard-page';
import { StartPage } from './start-page';

export const routes: Routes = [
	{ path: '', pathMatch: 'full', redirectTo: 'start' },
	{ path: 'start', component: StartPage },
	{ path: 'dashboard', component: LiveDashboardPage },
	{ path: '**', redirectTo: 'start' },
];
